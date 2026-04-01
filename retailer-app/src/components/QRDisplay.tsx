import { useEffect, useRef, useState } from "react";
import { QRCodeSVG } from "qrcode.react";
import { getTransactionStatus, cancelTransaction } from "../api/paymentApi";
import type { InitiateResponse, StatusResponse } from "../types";

interface Props {
  transaction: InitiateResponse;
  onComplete: (status: StatusResponse) => void;
  onCancel: () => void;
}

const STATUS_LABELS: Record<string, string> = {
  INITIATED: "Waiting for customer to scan...",
  QR_GENERATED: "Waiting for customer to scan...",
  CUSTOMER_OPENED: "Customer scanned QR...",
  CUSTOMER_CONFIRMED: "Customer confirming...",
  PROCESSING: "Processing with bank...",
};

const TERMINAL_STATES = new Set([
  "APPROVED",
  "REJECTED",
  "FAILED",
  "EXPIRED",
  "CANCELLED",
]);

export default function QRDisplay({ transaction, onComplete, onCancel }: Props) {
  const [statusText, setStatusText] = useState("Waiting for customer to scan...");
  const [cancelling, setCancelling] = useState(false);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    intervalRef.current = setInterval(async () => {
      try {
        const res = await getTransactionStatus(transaction.transactionId);
        setStatusText(STATUS_LABELS[res.status] ?? res.status);

        if (TERMINAL_STATES.has(res.status)) {
          if (intervalRef.current) clearInterval(intervalRef.current);
          onComplete(res);
        }
      } catch {
        // ignore transient errors, keep polling
      }
    }, 500);

    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
    };
  }, [transaction.transactionId, onComplete]);

  async function handleCancel() {
    setCancelling(true);
    try {
      await cancelTransaction(transaction.transactionId);
      if (intervalRef.current) clearInterval(intervalRef.current);
      onCancel();
    } catch {
      setCancelling(false);
    }
  }

  return (
    <div className="card qr-card">
      <h1>Scan to Pay</h1>
      <div className="qr-container">
        <QRCodeSVG value={transaction.qrPayload} size={280} level="M" />
      </div>
      <div className="qr-details">
        <p className="amount-display">
          {transaction.amount.toFixed(2)} {transaction.currency}
        </p>
        <p className="ref-display">Ref: {transaction.transactionRef}</p>
      </div>
      <p className="status-text">{statusText}</p>
      <button
        className="btn btn-cancel"
        onClick={handleCancel}
        disabled={cancelling}
      >
        {cancelling ? "Cancelling..." : "Cancel"}
      </button>
    </div>
  );
}
