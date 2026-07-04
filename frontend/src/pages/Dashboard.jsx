import React, { useEffect, useState, useContext } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';
import { AuthContext } from '../context/AuthContext';
import Sidebar from '../components/Sidebar';
import Header from '../components/Header';
import KpiCard from '../components/KpiCard';
import BarChart from '../components/BarChart';
import EmptyState from '../components/EmptyState';

export default function Dashboard() {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const { logout } = useContext(AuthContext);
  const navigate = useNavigate();

  // Drilldown Navigation States
  const [drillLevel, setDrillLevel] = useState(0); // 0: India, 1: Region, 2: State
  const [selectedDrillRegion, setSelectedDrillRegion] = useState('');
  const [selectedDrillState, setSelectedDrillState] = useState('');

  const fetchData = async () => {
    setLoading(true);
    setError('');
    try {
      const response = await api.get('/api/dashboard');
      setData(response.data);
    } catch (err) {
      console.error(err);
      if (err.response?.status === 401) {
        logout();
      } else {
        setError('Failed to fetch dashboard intelligence metrics.');
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const formatUSD = (cents) => {
    if (cents === undefined || cents === null) return '$0.00';
    return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(cents / 100);
  };

  if (loading) {
    return (
      <div className="app-container">
        <Sidebar />
        <div className="main-wrapper">
          <Header title="Dashboard" />
          <div className="content-body" style={{ justifyContent: 'center', alignItems: 'center' }}>
            <div style={{ fontWeight: '500', color: 'var(--text-secondary)' }}>Loading enterprise aggregation context...</div>
          </div>
        </div>
      </div>
    );
  }

  const hasData = data && data.totalTransactions > 0;

  // Prepare chart data
  const regionalChartData = hasData 
    ? Object.entries(data.regionalTotals).map(([label, value]) => ({ label, value })) 
    : [];

  const categoryChartData = hasData 
    ? Object.entries(data.categoryTotals).map(([label, value]) => ({ label, value })) 
    : [];



  const handleSelectRegion = (region) => {
    setSelectedDrillRegion(region);
    setDrillLevel(1);
  };

  const handleSelectState = (state) => {
    setSelectedDrillState(state);
    setDrillLevel(2);
  };

  const handleSelectCategory = (category) => {
    navigate(`/transactions?state=${encodeURIComponent(selectedDrillState)}&category=${encodeURIComponent(category)}`);
  };

  const resetDrill = (level) => {
    setDrillLevel(level);
    if (level === 0) {
      setSelectedDrillRegion('');
      setSelectedDrillState('');
    } else if (level === 1) {
      setSelectedDrillState('');
    }
  };

  return (
    <div className="app-container">
      <Sidebar />
      <div className="main-wrapper">
        <Header title="Executive Overview" />
        
        <div className="content-body">
          {error && <div className="alert alert-danger">{error}</div>}

          {!hasData ? (
            <EmptyState 
              title="No transactions available." 
              description="Import transaction data to begin analytics."
              actionText="Import Data"
              onAction={() => navigate('/transactions')}
            />
          ) : (
            <>
              {/* KPIs Grid */}
              <div className="grid-kpi">
                <KpiCard 
                  label="Total Transactions" 
                  value={data.totalTransactions.toLocaleString()} 
                  subtext={`Latest Import: ${data.latestImportTime || 'N/A'}`}
                />
                <KpiCard 
                  label="Total Revenue" 
                  value={formatUSD(data.totalRevenue)} 
                  subtext="Aggregated on server"
                />
                <KpiCard 
                  label="Avg Transaction Value" 
                  value={formatUSD(Math.round(data.averageTransactionValue))} 
                  subtext="Arithmetic Mean"
                />
                <KpiCard 
                  label="Top Region" 
                  value={data.topRegionName || 'N/A'} 
                  subtext={formatUSD(data.regionalTotals[data.topRegionName])}
                />
                <KpiCard 
                  label="Top Category" 
                  value={data.topCategoryName || 'N/A'} 
                  subtext={formatUSD(data.categoryTotals[data.topCategoryName])}
                />
                <KpiCard 
                  label="Sales Range" 
                  value={`${formatUSD(data.lowestTransactionValue)} - ${formatUSD(data.highestTransactionValue)}`} 
                  subtext="Min to Max Range"
                />
              </div>

              {/* Aggregation & Charts Area */}
              <div className="grid-charts">
                {/* Interactive Hierarchical Drill-down */}
                <div className="card" style={{ flexGrow: 2, minWidth: '450px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '15px' }}>
                    <h3 className="card-title" style={{ margin: 0 }}>Interactive Hierarchical Drill-down</h3>
                    <span className="badge badge-info" style={{ fontSize: '0.75rem', padding: '4px 8px', backgroundColor: 'rgba(79, 70, 229, 0.15)', color: 'var(--primary-btn)', borderRadius: '4px', fontWeight: '700' }}>
                      India Rollup Engine
                    </span>
                  </div>
                  
                  {/* Breadcrumbs Navigation */}
                  <div className="drilldown-breadcrumbs" style={{ display: 'flex', gap: '8px', alignItems: 'center', fontSize: '0.85rem', marginBottom: '20px', padding: '10px 14px', backgroundColor: 'var(--bg-color)', borderRadius: '6px', border: '1px solid var(--border-color)' }}>
                    <span 
                      style={{ cursor: 'pointer', fontWeight: drillLevel === 0 ? '700' : '500', color: drillLevel === 0 ? 'var(--primary-btn)' : 'var(--text-secondary)' }}
                      onClick={() => resetDrill(0)}
                    >
                      🇮🇳 India (All)
                    </span>
                    {drillLevel >= 1 && (
                      <>
                        <span style={{ color: 'var(--text-secondary)' }}>/</span>
                        <span 
                          style={{ cursor: 'pointer', fontWeight: drillLevel === 1 ? '700' : '500', color: drillLevel === 1 ? 'var(--primary-btn)' : 'var(--text-secondary)' }}
                          onClick={() => resetDrill(1)}
                        >
                          {selectedDrillRegion}
                        </span>
                      </>
                    )}
                    {drillLevel >= 2 && (
                      <>
                        <span style={{ color: 'var(--text-secondary)' }}>/</span>
                        <span style={{ fontWeight: '700', color: 'var(--primary-btn)' }}>
                          {selectedDrillState}
                        </span>
                      </>
                    )}
                  </div>

                  {/* Level 0: India */}
                  {drillLevel === 0 && (
                    <div className="table-container">
                      <table className="corporate-table">
                        <thead>
                          <tr>
                            <th>Region Name</th>
                            <th style={{ textAlign: 'right' }}>Total Revenue (USD)</th>
                            <th style={{ textAlign: 'right' }}>State Count</th>
                            <th style={{ textAlign: 'center' }}>Action</th>
                          </tr>
                        </thead>
                        <tbody>
                          {Object.entries(data.regionalTotals).map(([region, total]) => {
                            const statesCount = data.regionStates?.[region]?.length || 0;
                            return (
                              <tr key={region} style={{ cursor: 'pointer' }} onClick={() => handleSelectRegion(region)}>
                                <td style={{ fontWeight: '600' }}>{region}</td>
                                <td style={{ textAlign: 'right', fontWeight: '600', color: 'var(--primary-btn)' }}>{formatUSD(total)}</td>
                                <td style={{ textAlign: 'right' }}>{statesCount} States</td>
                                <td style={{ textAlign: 'center' }}>
                                  <button className="btn btn-outline" style={{ padding: '4px 10px', fontSize: '0.75rem', border: '1px solid var(--border-color)', borderRadius: '4px', background: 'transparent', cursor: 'pointer', color: 'var(--text-primary)' }}>
                                    Drill Down →
                                  </button>
                                </td>
                              </tr>
                            );
                          })}
                        </tbody>
                      </table>
                    </div>
                  )}

                  {/* Level 1: Region */}
                  {drillLevel === 1 && (
                    <div className="table-container">
                      <table className="corporate-table">
                        <thead>
                          <tr>
                            <th>State (in {selectedDrillRegion})</th>
                            <th style={{ textAlign: 'right' }}>Total Sales (USD)</th>
                            <th style={{ textAlign: 'center' }}>Action</th>
                          </tr>
                        </thead>
                        <tbody>
                          {(data.regionStates?.[selectedDrillRegion] || []).map(state => {
                            const stateTotal = data.stateTotals?.[state] || 0;
                            return (
                              <tr key={state} style={{ cursor: 'pointer' }} onClick={() => handleSelectState(state)}>
                                <td style={{ fontWeight: '600' }}>{state}</td>
                                <td style={{ textAlign: 'right', fontWeight: '600', color: 'var(--success-color)' }}>{formatUSD(stateTotal)}</td>
                                <td style={{ textAlign: 'center' }}>
                                  <button className="btn btn-outline" style={{ padding: '4px 10px', fontSize: '0.75rem', border: '1px solid var(--border-color)', borderRadius: '4px', background: 'transparent', cursor: 'pointer', color: 'var(--text-primary)' }}>
                                    View Categories →
                                  </button>
                                </td>
                              </tr>
                            );
                          })}
                          {(!data.regionStates?.[selectedDrillRegion] || data.regionStates[selectedDrillRegion].length === 0) && (
                            <tr>
                              <td colSpan="3" style={{ textAlign: 'center', color: 'var(--text-secondary)' }}>No states recorded in this region.</td>
                            </tr>
                          )}
                        </tbody>
                      </table>
                    </div>
                  )}

                  {/* Level 2: State */}
                  {drillLevel === 2 && (
                    <div className="table-container">
                      <table className="corporate-table">
                        <thead>
                          <tr>
                            <th>Product Category</th>
                            <th style={{ textAlign: 'right' }}>Total Sales in {selectedDrillState} (USD)</th>
                            <th style={{ textAlign: 'center' }}>Action</th>
                          </tr>
                        </thead>
                        <tbody>
                          {Object.entries(data.stateCategorySales?.[selectedDrillState] || {}).map(([category, amount]) => (
                            <tr key={category} style={{ cursor: 'pointer' }} onClick={() => handleSelectCategory(category)}>
                              <td style={{ fontWeight: '600' }}>{category}</td>
                              <td style={{ textAlign: 'right', fontWeight: '600' }}>{formatUSD(amount)}</td>
                              <td style={{ textAlign: 'center' }}>
                                <button className="btn btn-primary" style={{ padding: '4px 10px', fontSize: '0.75rem', backgroundColor: 'var(--primary-btn)', border: 'none', color: '#fff', borderRadius: '4px', cursor: 'pointer' }}>
                                  View Transactions 🔗
                                </button>
                              </td>
                            </tr>
                          ))}
                          {(!data.stateCategorySales?.[selectedDrillState] || Object.keys(data.stateCategorySales[selectedDrillState]).length === 0) && (
                            <tr>
                              <td colSpan="3" style={{ textAlign: 'center', color: 'var(--text-secondary)' }}>No category sales in this state.</td>
                            </tr>
                          )}
                        </tbody>
                      </table>
                    </div>
                  )}
                </div>

                {/* Regional & Category Rankings Visualizations */}
                <div style={{ display: 'flex', flexDirection: 'column', gap: '25px' }}>
                  <div className="card">
                    <h3 className="card-title">Regional Revenue Distribution</h3>
                    <BarChart data={regionalChartData} formatter={formatUSD} />
                  </div>
                  <div className="card">
                    <h3 className="card-title">Category Revenue Distribution</h3>
                    <BarChart data={categoryChartData} formatter={formatUSD} />
                  </div>
                </div>
              </div>

              {/* Pivot Tables Limitation Explanation Statement */}
              <div className="card" style={{ marginTop: '25px', background: 'linear-gradient(135deg, rgba(79, 70, 229, 0.05) 0%, rgba(192, 132, 252, 0.05) 100%)', border: '1px solid rgba(79, 70, 229, 0.15)', borderRadius: 'var(--border-radius)' }}>
                <h3 className="card-title" style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--primary-btn)', margin: '0 0 12px 0' }}>
                  💡 Why SalesSphere BI Aggregation Engine?
                </h3>
                <div style={{ color: 'var(--text-secondary)', fontSize: '0.9rem', lineHeight: '1.6' }}>
                  <p style={{ marginBottom: '10px' }}>
                    Traditional spreadsheet pivot tables become increasingly <strong>slow, manual, error-prone, and brittle</strong> as transaction volumes scale into tens or hundreds of thousands of records. Manual macro formulas frequently break during multi-user edits and lack data type or business rule validation.
                  </p>
                  <p style={{ margin: 0 }}>
                    SalesSphere BI solves this by implementing an automated <strong>in-memory Java Collections Rollup Engine</strong>. Raw transactions are parsed, cleaned, and enriched (automatically deriving regions from states) during the preprocessing pipeline before being stored in MySQL. Aggregations occur in-memory at <strong>O(N) time complexity</strong> using optimized Java structures, eliminating SQL group operations and providing a fast, reusable, and enterprise-grade BI pipeline.
                  </p>
                </div>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
