import React, { useContext } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, AuthContext } from './context/AuthContext';
import Login from './pages/Login';
import Register from './pages/Register';
import Dashboard from './pages/Dashboard';
import Transactions from './pages/Transactions';
import Analytics from './pages/Analytics';
import Reports from './pages/Reports';

const AccessDenied = () => {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100vh', backgroundColor: 'var(--bg-color)', color: 'var(--text-primary)' }}>
      <div style={{ fontSize: '4rem', marginBottom: '20px' }}>⚠️</div>
      <h2 style={{ fontSize: '1.75rem', fontWeight: '700', marginBottom: '10px' }}>403 - Access Denied</h2>
      <p style={{ color: 'var(--text-secondary)', marginBottom: '25px', textAlign: 'center', maxWidth: '400px' }}>
        You do not have the required permissions to access this screen. Please contact your system administrator.
      </p>
      <button 
        className="btn btn-primary" 
        onClick={() => window.location.href = '/dashboard'}
        style={{ padding: '10px 20px', border: 'none', borderRadius: '4px', backgroundColor: 'var(--primary-btn)', color: '#fff', cursor: 'pointer', fontWeight: '600' }}
      >
        Go to Dashboard
      </button>
    </div>
  );
};

// Protected Route wrapper component
const ProtectedRoute = ({ children, allowedRoles }) => {
  const { isAuthenticated, user } = useContext(AuthContext);
  
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (allowedRoles && !allowedRoles.includes(user?.role)) {
    return <AccessDenied />;
  }

  return children;
};

export default function App() {
  return (
    <AuthProvider>
      <Router>
        <Routes>
          {/* Public Routes */}
          <Route path="/login" element={<RouteIfGuest><Login /></RouteIfGuest>} />
          <Route path="/register" element={<Navigate to="/login" replace />} />

          {/* Protected BI Dashboard Portal */}
          <Route path="/dashboard" element={
            <ProtectedRoute allowedRoles={['ROLE_ADMINISTRATOR', 'ROLE_BUSINESS_ANALYST', 'ROLE_CEO']}>
              <Dashboard />
            </ProtectedRoute>
          } />
          <Route path="/transactions" element={
            <ProtectedRoute allowedRoles={['ROLE_ADMINISTRATOR', 'ROLE_BUSINESS_ANALYST']}>
              <Transactions />
            </ProtectedRoute>
          } />
          <Route path="/analytics" element={
            <ProtectedRoute allowedRoles={['ROLE_ADMINISTRATOR', 'ROLE_BUSINESS_ANALYST']}>
              <Analytics />
            </ProtectedRoute>
          } />
          <Route path="/reports" element={
            <ProtectedRoute allowedRoles={['ROLE_ADMINISTRATOR', 'ROLE_BUSINESS_ANALYST', 'ROLE_CEO']}>
              <Reports />
            </ProtectedRoute>
          } />

          {/* Catch-all redirect */}
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </Router>
    </AuthProvider>
  );
}

// Redirect authenticated users away from Login/Register pages
const RouteIfGuest = ({ children }) => {
  const { isAuthenticated } = useContext(AuthContext);
  return !isAuthenticated ? children : <Navigate to="/dashboard" replace />;
};
