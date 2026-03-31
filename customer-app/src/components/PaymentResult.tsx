import type { PaymentResult as PaymentResultType } from '../types';

interface Props {
  result: PaymentResultType | null;
  loading: boolean;
}

export default function PaymentResult({ result, loading }: Props) {
  if (loading) {
    return (
      <div className="card">
        <div className="card-body result-container">
          <div className="spinner" />
          <p className="result-text">Processing your payment...</p>
        </div>
      </div>
    );
  }

  if (!result) {
    return (
      <div className="card">
        <div className="card-body result-container">
          <div className="result-icon result-error">&#10007;</div>
          <h2>Something went wrong</h2>
          <p className="result-text">Unable to retrieve payment result.</p>
        </div>
      </div>
    );
  }

  const status = result.status.toUpperCase();

  if (status === 'APPROVED') {
    return (
      <div className="card">
        <div className="card-body result-container">
          <div className="result-icon result-success animate-pop">&#10003;</div>
          <h2>Payment Successful</h2>
          <p className="result-text">{result.message}</p>
        </div>
      </div>
    );
  }

  if (status === 'EXPIRED') {
    return (
      <div className="card">
        <div className="card-body result-container">
          <div className="result-icon result-error">&#9201;</div>
          <h2>Payment Expired</h2>
          <p className="result-text">
            This payment request has timed out. Please request a new payment link from the merchant.
          </p>
        </div>
      </div>
    );
  }

  // REJECTED, FAILED, or other
  return (
    <div className="card">
      <div className="card-body result-container">
        <div className="result-icon result-error">&#10007;</div>
        <h2>Payment {status === 'REJECTED' ? 'Rejected' : 'Failed'}</h2>
        <p className="result-text">{result.message}</p>
      </div>
    </div>
  );
}
