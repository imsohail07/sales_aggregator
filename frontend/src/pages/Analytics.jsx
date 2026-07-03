import React, { useEffect, useState, useContext } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';
import { AuthContext } from '../context/AuthContext';
import Sidebar from '../components/Sidebar';
import Header from '../components/Header';
import EmptyState from '../components/EmptyState';

export default function Analytics() {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const { logout } = useContext(AuthContext);
  const navigate = useNavigate();

  const fetchAnalytics = async () => {
    setLoading(true);
    setError('');
    try {
      const response = await api.get('/api/analytics');
      setData(response.data);
    } catch (err) {
      console.error(err);
      if (err.response?.status === 401) {
        logout();
      } else {
        setError('Failed to fetch business intelligence analytics.');
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAnalytics();
  }, []);

  const formatUSD = (amount) => {
    if (amount === undefined || amount === null) return '$0.00';
    return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(amount);
  };

  if (loading) {
    return (
      <div className="app-container">
        <Sidebar />
        <div className="main-wrapper">
          <Header title="Business Intelligence Analytics" />
          <div className="content-body" style={{ justifyContent: 'center', alignItems: 'center' }}>
            <div style={{ fontWeight: '500', color: 'var(--text-secondary)' }}>Loading sales analytics context...</div>
          </div>
        </div>
      </div>
    );
  }

  const hasData = data && data.totalTransactions > 0;

  return (
    <div className="app-container">
      <Sidebar />
      <div className="main-wrapper">
        <Header title="Market Analytics" />

        <div className="content-body">
          {error && <div className="alert alert-danger">{error}</div>}

          {!hasData ? (
            <EmptyState 
              title="No regional statistics available." 
              description="Import transaction data to begin analytics."
              actionText="Import Data"
              onAction={() => navigate('/transactions')}
            />
          ) : (
            <>
              {/* Summary KPIs */}
              <div className="grid-kpi" style={{ gridTemplateColumns: 'repeat(3, 1fr)' }}>
                <div className="card kpi-card">
                  <span className="kpi-label">Market Capitalization (Total Revenue)</span>
                  <span className="kpi-value">{formatUSD(data.totalRevenue)}</span>
                  <span className="kpi-subtext">Sum of all regions</span>
                </div>
                <div className="card kpi-card">
                  <span className="kpi-label">Active Sales Regions</span>
                  <span className="kpi-value">{data.regionalRankings.length}</span>
                  <span className="kpi-subtext">Geographic clusters</span>
                </div>
                <div className="card kpi-card">
                  <span className="kpi-label">Product Categories traded</span>
                  <span className="kpi-value">{data.categoryRankings.length}</span>
                  <span className="kpi-subtext">Classifications</span>
                </div>
              </div>

              {/* Rankings side by side */}
              <div className="grid-charts">
                {/* Region rankings */}
                <div className="card">
                  <h3 className="card-title">Territory Sales Rankings</h3>
                  <div className="table-container">
                    <table className="corporate-table">
                      <thead>
                        <tr>
                          <th style={{ width: '60px' }}>Rank</th>
                          <th>Region Name</th>
                          <th style={{ textAlign: 'right' }}>Total Revenue</th>
                          <th style={{ textAlign: 'right', width: '140px' }}>Market Share (%)</th>
                        </tr>
                      </thead>
                      <tbody>
                        {data.regionalRankings.map((r) => (
                          <tr key={r.regionName}>
                            <td style={{ fontWeight: '700' }}>#{r.rank}</td>
                            <td style={{ fontWeight: '600' }}>{r.regionName}</td>
                            <td style={{ textAlign: 'right', fontWeight: '500' }}>{formatUSD(r.totalSales)}</td>
                            <td style={{ textAlign: 'right' }}>
                              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: '4px' }}>
                                <span style={{ fontWeight: '700' }}>{r.percentageShare}%</span>
                                <div style={{ width: '100px', height: '6px', backgroundColor: 'var(--bg-color)', borderRadius: '2px', border: '1px solid var(--border-color)', overflow: 'hidden' }}>
                                  <div style={{ width: `${r.percentageShare}%`, height: '100%', backgroundColor: 'var(--primary-btn)' }}></div>
                                </div>
                              </div>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>

                {/* Category rankings */}
                <div className="card">
                  <h3 className="card-title">Product Category Rankings</h3>
                  <div className="table-container">
                    <table className="corporate-table">
                      <thead>
                        <tr>
                          <th style={{ width: '60px' }}>Rank</th>
                          <th>Category Name</th>
                          <th style={{ textAlign: 'right' }}>Total Revenue</th>
                          <th style={{ textAlign: 'right', width: '140px' }}>Revenue Share (%)</th>
                        </tr>
                      </thead>
                      <tbody>
                        {data.categoryRankings.map((c) => (
                          <tr key={c.categoryName}>
                            <td style={{ fontWeight: '700' }}>#{c.rank}</td>
                            <td style={{ fontWeight: '600' }}>{c.categoryName}</td>
                            <td style={{ textAlign: 'right', fontWeight: '500' }}>{formatUSD(c.totalSales)}</td>
                            <td style={{ textAlign: 'right' }}>
                              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: '4px' }}>
                                <span style={{ fontWeight: '700' }}>{c.percentageShare}%</span>
                                <div style={{ width: '100px', height: '6px', backgroundColor: 'var(--bg-color)', borderRadius: '2px', border: '1px solid var(--border-color)', overflow: 'hidden' }}>
                                  <div style={{ width: `${c.percentageShare}%`, height: '100%', backgroundColor: 'var(--primary-btn)' }}></div>
                                </div>
                              </div>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
