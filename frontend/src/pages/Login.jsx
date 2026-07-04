import React, { useState, useContext } from 'react';
import { useNavigate } from 'react-router-dom';
import { AuthContext } from '../context/AuthContext';
import api from '../services/api';

export default function Login() {
  const [role, setRole] = useState('ROLE_ADMINISTRATOR');
  const [email, setEmail] = useState('admin@salessphere.com');
  const [password, setPassword] = useState('Admin@123');
  const [rememberMe, setRememberMe] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  
  const { login, theme, toggleTheme } = useContext(AuthContext);
  const navigate = useNavigate();

  const handleRoleChange = (selectedRole) => {
    setRole(selectedRole);
    if (selectedRole === 'ROLE_ADMINISTRATOR') {
      setEmail('admin@salessphere.com');
      setPassword('Admin@123');
    } else if (selectedRole === 'ROLE_BUSINESS_ANALYST') {
      setEmail('analyst@salessphere.com');
      setPassword('Analyst@123');
    } else if (selectedRole === 'ROLE_CEO') {
      setEmail('ceo@salessphere.com');
      setPassword('CEO@123');
    }
  };

  const handleForgotPassword = (e) => {
    e.preventDefault();
    alert("Enterprise password recovery request initiated. System Administrator will contact you shortly.");
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    if (!email || !password || !role) {
      setError('Please fill in all fields.');
      return;
    }

    setLoading(true);
    try {
      // Send email as the username payload for backend multi-lookup compatibility
      const response = await api.post('/api/auth/login', { username: email, password });
      
      const userRole = response.data.role;
      
      // Strict role matching verification
      if (userRole !== role) {
        const getLabel = (r) => {
          if (r === 'ROLE_ADMINISTRATOR') return 'Administrator';
          if (r === 'ROLE_BUSINESS_ANALYST') return 'Business Analyst';
          if (r === 'ROLE_CEO') return 'CEO';
          return r;
        };
        setError(`Access denied. Your corporate profile is registered as ${getLabel(userRole)}, not ${getLabel(role)}.`);
        setLoading(false);
        return;
      }

      login(response.data.token, {
        id: response.data.id,
        username: response.data.username,
        email: response.data.email,
        role: response.data.role
      });

      // Role Redirect Workflow
      if (userRole === 'ROLE_ADMINISTRATOR') {
        navigate('/dashboard');
      } else if (userRole === 'ROLE_BUSINESS_ANALYST') {
        navigate('/analytics');
      } else if (userRole === 'ROLE_CEO') {
        navigate('/dashboard');
      } else {
        navigate('/dashboard');
      }
    } catch (err) {
      console.error(err);
      setError(
        err.response?.data?.message || 
        'Authentication failed. Please verify your corporate email and password.'
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page" style={{ position: 'relative' }}>
      {/* Light / Dark Mode Toggle button */}
      <div style={{ position: 'absolute', top: '25px', right: '25px' }}>
        <button 
          onClick={toggleTheme} 
          style={{ 
            padding: '8px 14px', 
            border: '1px solid var(--border-color)', 
            borderRadius: '6px', 
            backgroundColor: 'var(--card-bg)', 
            color: 'var(--text-primary)', 
            cursor: 'pointer', 
            fontSize: '0.8rem', 
            fontWeight: '600',
            transition: 'all 0.2s ease',
            display: 'flex',
            alignItems: 'center',
            gap: '6px'
          }}
        >
          {theme === 'light' ? '🌙 Dark Mode' : '☀️ Light Mode'}
        </button>
      </div>

      <div className="card auth-card" style={{ padding: '30px', boxShadow: '0 20px 40px rgba(0, 0, 0, 0.15)', border: '1px solid var(--border-color)' }}>
        <div className="auth-header">
          <div className="auth-logo" style={{ fontSize: '2rem', fontWeight: '800', background: 'linear-gradient(135deg, #4f46e5 0%, #c084fc 100%)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
            SalesSphere BI
          </div>
          <p className="auth-subtitle" style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
            Enterprise Sales Aggregation & Corporate BI Portal
          </p>
        </div>

        {error && (
          <div className="alert alert-danger" style={{ fontSize: '0.85rem', padding: '10px 14px', borderRadius: '6px', marginBottom: '15px' }}>
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit}>
          <div className="form-group" style={{ marginBottom: '15px' }}>
            <label className="form-label" htmlFor="email" style={{ display: 'block', marginBottom: '6px' }}>Corporate Email</label>
            <input
              type="email"
              id="email"
              className="form-input"
              placeholder="name@salessphere.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              disabled={loading}
              required
            />
          </div>

          <div className="form-group" style={{ marginBottom: '15px' }}>
            <label className="form-label" htmlFor="password" style={{ display: 'block', marginBottom: '6px' }}>Security Password</label>
            <input
              type="password"
              id="password"
              className="form-input"
              placeholder="••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              disabled={loading}
              required
            />
          </div>

          <div className="form-group" style={{ marginBottom: '15px' }}>
            <label className="form-label" htmlFor="role" style={{ display: 'block', marginBottom: '6px' }}>Access Role Profile</label>
            <select
              id="role"
              className="form-select"
              value={role}
              onChange={(e) => handleRoleChange(e.target.value)}
              disabled={loading}
              required
            >
              <option value="ROLE_ADMINISTRATOR">Administrator</option>
              <option value="ROLE_BUSINESS_ANALYST">Business Analyst</option>
              <option value="ROLE_CEO">CEO</option>
            </select>
          </div>

          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px', fontSize: '0.85rem' }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: '6px', color: 'var(--text-secondary)', cursor: 'pointer' }}>
              <input
                type="checkbox"
                checked={rememberMe}
                onChange={(e) => setRememberMe(e.target.checked)}
                style={{ cursor: 'pointer' }}
              />
              Remember Me
            </label>
            <a href="#forgot" onClick={handleForgotPassword} style={{ color: 'var(--primary-btn)', textDecoration: 'none', fontWeight: '700' }}>
              Forgot Password?
            </a>
          </div>

          <button 
            type="submit" 
            className="btn btn-primary" 
            style={{ width: '100%', padding: '12px', fontSize: '0.95rem', fontWeight: '700', borderRadius: '6px' }}
            disabled={loading}
          >
            {loading ? 'Authenticating Profile...' : 'Authorize Login'}
          </button>
        </form>
      </div>
    </div>
  );
}
