interface Props {
  status: string;
  amount: number;
  currency: string;
  transactionRef: string;
  bankReference?: string;
  onNewPayment: () => void;
}

export default function Result({
  status,
  amount,
  currency,
  transactionRef,
  bankReference,
  onNewPayment,
}: Props) {
  const isSuccess = status === "APPROVED";

  const failureReasons: Record<string, string> = {
    REJECTED: "Payment was rejected by the bank.",
    FAILED: "Payment failed. Please try again.",
    EXPIRED: "Payment expired. The customer did not complete in time.",
    CANCELLED: "Payment was cancelled.",
  };

  return (
    <div className="card result-card">
      {isSuccess ? (
        <>
          <div className="result-icon success-icon">&#10003;</div>
          <h1 className="success-text">Payment Successful</h1>
        </>
      ) : (
        <>
          <div className="result-icon failure-icon">&#10007;</div>
          <h1 className="failure-text">
            {failureReasons[status] ?? `Payment ${status}`}
          </h1>
        </>
      )}
      <div className="result-details">
        <p className="amount-display">
          {amount.toFixed(2)} {currency}
        </p>
        <p className="ref-display">Ref: {transactionRef}</p>
        {bankReference && (
          <p className="ref-display">Bank Ref: {bankReference}</p>
        )}
      </div>
      <button className="btn btn-primary" onClick={onNewPayment}>
        New Payment
      </button>
    </div>
  );
}
