import { useState } from "react";
import { initiatePayment } from "../api/paymentApi";
import type { InitiateResponse } from "../types";

interface Props {
  onPaymentInitiated: (response: InitiateResponse) => void;
}

export default function NewPayment({ onPaymentInitiated }: Props) {
  const [amount, setAmount] = useState("");
  const [description, setDescription] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError("");

    const numAmount = parseFloat(amount);
    if (isNaN(numAmount) || numAmount <= 0) {
      setError("Please enter a valid amount.");
      return;
    }

    setLoading(true);
    try {
      const response = await initiatePayment(numAmount, description);
      onPaymentInitiated(response);
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message);
      } else {
        setError("Failed to initiate payment.");
      }
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="card">
      <h1>New Payment</h1>
      <form onSubmit={handleSubmit}>
        <div className="form-group">
          <label htmlFor="amount">Amount (EUR)</label>
          <input
            id="amount"
            type="number"
            step="0.01"
            min="0.01"
            placeholder="0.00"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            required
          />
        </div>
        <div className="form-group">
          <label htmlFor="description">Description</label>
          <textarea
            id="description"
            placeholder="Payment description..."
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            rows={3}
          />
        </div>
        {error && <p className="error-text">{error}</p>}
        <button type="submit" className="btn btn-primary" disabled={loading}>
          {loading ? "Processing..." : "Pay with SEPA Instant"}
        </button>
      </form>
    </div>
  );
}
