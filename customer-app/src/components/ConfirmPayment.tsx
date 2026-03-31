import { useState } from 'react';
import type { ConfirmPaymentData, PaymentDetails } from '../types';
import { formatIban, validateIban } from '../utils/ibanValidator';

interface Props {
  paymentDetails: PaymentDetails;
  onConfirm: (data: ConfirmPaymentData) => void;
  loading?: boolean;
}

export default function ConfirmPayment({ paymentDetails, onConfirm, loading }: Props) {
  const [iban, setIban] = useState('');
  const [name, setName] = useState('');
  const [consent, setConsent] = useState(false);
  const [touched, setTouched] = useState(false);

  const ibanValidation = validateIban(iban);
  const isValid = ibanValidation.valid && consent;

  const formattedAmount = new Intl.NumberFormat('en-EU', {
    style: 'currency',
    currency: paymentDetails.currency || 'EUR',
    minimumFractionDigits: 2,
  }).format(paymentDetails.amount);

  function handleIbanChange(e: React.ChangeEvent<HTMLInputElement>) {
    const raw = e.target.value.replace(/\s/g, '');
    setIban(raw);
    if (!touched) setTouched(true);
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!isValid || loading) return;
    onConfirm({
      debtorIban: iban.replace(/\s/g, '').toUpperCase(),
      debtorName: name || undefined,
      consentGiven: true,
    });
  }

  return (
    <div className="card">
      <div className="card-header">
        <h2>Confirm Payment</h2>
        <div className="amount-display-small">{formattedAmount}</div>
      </div>
      <form className="card-body" onSubmit={handleSubmit}>
        <div className="form-group">
          <label htmlFor="iban">Your IBAN</label>
          <div className="input-wrapper">
            <input
              id="iban"
              type="text"
              className={`input ${touched ? (ibanValidation.valid ? 'input-valid' : 'input-error') : ''}`}
              placeholder="e.g. RO49 AAAA 1B31 0075 9384 0000"
              value={formatIban(iban)}
              onChange={handleIbanChange}
              disabled={loading}
              autoComplete="off"
            />
            {touched && ibanValidation.valid && (
              <span className="input-icon valid-icon">&#10003;</span>
            )}
          </div>
          {touched && !ibanValidation.valid && ibanValidation.error && (
            <div className="field-error">{ibanValidation.error}</div>
          )}
        </div>

        <div className="form-group">
          <label htmlFor="name">Your Name (optional)</label>
          <input
            id="name"
            type="text"
            className="input"
            placeholder="Full name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            disabled={loading}
          />
        </div>

        <div className="form-group consent-group">
          <label className="checkbox-label">
            <input
              type="checkbox"
              checked={consent}
              onChange={(e) => setConsent(e.target.checked)}
              disabled={loading}
            />
            <span>
              I authorize this SEPA Instant Credit Transfer of{' '}
              <strong>{formattedAmount}</strong> to{' '}
              <strong>{paymentDetails.creditorName}</strong>
            </span>
          </label>
        </div>

        <div className="card-footer">
          <button
            type="submit"
            className="btn btn-primary"
            disabled={!isValid || loading}
          >
            {loading ? (
              <span className="btn-loading">
                <span className="spinner-small" /> Processing...
              </span>
            ) : (
              'Confirm & Pay'
            )}
          </button>
        </div>
      </form>
    </div>
  );
}
