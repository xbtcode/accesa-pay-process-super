import { BrowserRouter, Route, Routes } from 'react-router-dom';
import PayPage from './pages/PayPage';
import './App.css';

function LandingPage() {
  return (
    <div className="page-container">
      <div className="card">
        <div className="card-body result-container">
          <h1 className="landing-title">SEPA Payment</h1>
          <p className="result-text">
            Use a payment link provided by your merchant to proceed.
          </p>
        </div>
      </div>
    </div>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <div className="app-shell">
        <Routes>
          <Route path="/pay/:ref" element={<PayPage />} />
          <Route path="*" element={<LandingPage />} />
        </Routes>
      </div>
    </BrowserRouter>
  );
}
