import type { PaymentDetails as PaymentDetailsType } from '../types';

interface Props {
  paymentDetails: PaymentDetailsType;
  onProceed: () => void;
}

export default function PaymentDetails({ paymentDetails, onProceed }: Props) {
  const formattedAmount = new Intl.NumberFormat('en-EU', {
    style: 'currency',
    currency: paymentDetails.currency || 'EUR',
    minimumFractionDigits: 2,
  }).format(paymentDetails.amount);

  return (
    <div className="card">
      <div className="card-header">
        <h2>Payment Request</h2>
      </div>
      <div className="card-body">
        <div className="merchant-name">{paymentDetails.merchantName}</div>
        <div className="amount-display">{formattedAmount}</div>
        {paymentDetails.description && (
          <div className="description">{paymentDetails.description}</div>
        )}
        <div className="details-row">
          <span className="label">Recipient</span>
          <span className="value">{paymentDetails.creditorName}</span>
        </div>
        <div className="details-row">
          <span className="label">Reference</span>
          <span className="value ref-value">{paymentDetails.transactionRef}</span>
        </div>
      </div>
      <div className="card-footer">
        <button className="btn btn-primary" onClick={onProceed}>
          Proceed to Pay
        </button>
      </div>
    </div>
  );
}
