import { useCallback, useEffect, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import ConfirmPayment from '../components/ConfirmPayment';
import PaymentDetailsComponent from '../components/PaymentDetails';
import PaymentResultComponent from '../components/PaymentResult';
import { confirmPayment, getPaymentDetails, getPaymentResult } from '../api/paymentApi';
import type { ConfirmPaymentData, PaymentDetails, PaymentResult } from '../types';

type Stage = 'loading' | 'details' | 'confirm' | 'processing' | 'result';

const TERMINAL_STATUSES = ['APPROVED', 'REJECTED', 'FAILED', 'EXPIRED'];
const POLL_INTERVAL_MS = 1000;
const MAX_POLL_MS = 30000;

export default function PayPage() {
  const { ref } = useParams<{ ref: string }>();
  const [stage, setStage] = useState<Stage>('loading');
  const [paymentDetails, setPaymentDetails] = useState<PaymentDetails | null>(null);
  const [result, setResult] = useState<PaymentResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const pollTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const pollStartRef = useRef<number>(0);

  useEffect(() => {
    if (!ref) return;
    let cancelled = false;

    getPaymentDetails(ref)
      .then((data) => {
        if (cancelled) return;
        setPaymentDetails(data);
        const status = data.status.toUpperCase();
        if (TERMINAL_STATUSES.includes(status)) {
          setResult({
            transactionRef: data.transactionRef,
            status: data.status,
            message: '',
            completedAt: '',
          });
          setStage('result');
        } else {
          setStage('details');
        }
      })
      .catch(() => {
        if (cancelled) return;
        setError('Payment not found or link is invalid.');
        setStage('result');
      });

    return () => {
      cancelled = true;
    };
  }, [ref]);

  const stopPolling = useCallback(() => {
    if (pollTimerRef.current) {
      clearTimeout(pollTimerRef.current);
      pollTimerRef.current = null;
    }
  }, []);

  const pollResult = useCallback(() => {
    if (!ref) return;

    const elapsed = Date.now() - pollStartRef.current;
    if (elapsed >= MAX_POLL_MS) {
      stopPolling();
      setResult({
        transactionRef: ref,
        status: 'EXPIRED',
        message: 'Payment processing timed out. Please check with your bank.',
        completedAt: '',
      });
      setStage('result');
      return;
    }

    getPaymentResult(ref)
      .then((data) => {
        if (TERMINAL_STATUSES.includes(data.status.toUpperCase())) {
          stopPolling();
          setResult(data);
          setStage('result');
        } else {
          pollTimerRef.current = setTimeout(pollResult, POLL_INTERVAL_MS);
        }
      })
      .catch(() => {
        // Keep polling on transient errors
        pollTimerRef.current = setTimeout(pollResult, POLL_INTERVAL_MS);
      });
  }, [ref, stopPolling]);

  useEffect(() => {
    return () => stopPolling();
  }, [stopPolling]);

  function handleProceed() {
    setStage('confirm');
  }

  async function handleConfirm(data: ConfirmPaymentData) {
    if (!ref) return;
    setSubmitting(true);
    try {
      await confirmPayment(ref, data);
      setStage('processing');
      pollStartRef.current = Date.now();
      pollResult();
    } catch {
      setError('Failed to submit payment. Please try again.');
    } finally {
      setSubmitting(false);
    }
  }

  if (stage === 'loading') {
    return (
      <div className="page-container">
        <div className="card">
          <div className="card-body result-container">
            <div className="spinner" />
            <p className="result-text">Loading payment details...</p>
          </div>
        </div>
      </div>
    );
  }

  if (error && stage !== 'confirm') {
    return (
      <div className="page-container">
        <div className="card">
          <div className="card-body result-container">
            <div className="result-icon result-error">&#10007;</div>
            <h2>Error</h2>
            <p className="result-text">{error}</p>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="page-container">
      {stage === 'details' && paymentDetails && (
        <PaymentDetailsComponent
          paymentDetails={paymentDetails}
          onProceed={handleProceed}
        />
      )}
      {stage === 'confirm' && paymentDetails && (
        <>
          {error && <div className="error-banner">{error}</div>}
          <ConfirmPayment
            paymentDetails={paymentDetails}
            onConfirm={handleConfirm}
            loading={submitting}
          />
        </>
      )}
      {(stage === 'processing' || stage === 'result') && (
        <PaymentResultComponent
          result={result}
          loading={stage === 'processing'}
        />
      )}
    </div>
  );
}
