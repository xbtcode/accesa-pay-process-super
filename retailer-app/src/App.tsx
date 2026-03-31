import { useCallback, useState } from "react";
import NewPayment from "./components/NewPayment";
import QRDisplay from "./components/QRDisplay";
import Result from "./components/Result";
import type { InitiateResponse, StatusResponse } from "./types";
import "./App.css";

type Screen = "new-payment" | "qr-display" | "result";

export default function App() {
  const [screen, setScreen] = useState<Screen>("new-payment");
  const [transaction, setTransaction] = useState<InitiateResponse | null>(null);
  const [finalStatus, setFinalStatus] = useState<StatusResponse | null>(null);

  function handlePaymentInitiated(response: InitiateResponse) {
    setTransaction(response);
    setScreen("qr-display");
  }

  const handleComplete = useCallback((status: StatusResponse) => {
    setFinalStatus(status);
    setScreen("result");
  }, []);

  function handleCancel() {
    setScreen("new-payment");
    setTransaction(null);
  }

  function handleNewPayment() {
    setScreen("new-payment");
    setTransaction(null);
    setFinalStatus(null);
  }

  return (
    <div className="app-container">
      <header className="app-header">
        <h2>Retailer POS</h2>
      </header>
      <main className="app-main">
        {screen === "new-payment" && (
          <NewPayment onPaymentInitiated={handlePaymentInitiated} />
        )}
        {screen === "qr-display" && transaction && (
          <QRDisplay
            transaction={transaction}
            onComplete={handleComplete}
            onCancel={handleCancel}
          />
        )}
        {screen === "result" && finalStatus && (
          <Result
            status={finalStatus.status}
            amount={finalStatus.amount}
            currency={finalStatus.currency}
            transactionRef={finalStatus.transactionRef}
            bankReference={finalStatus.bankReference}
            onNewPayment={handleNewPayment}
          />
        )}
      </main>
    </div>
  );
}
