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
                {/* Nested Aggregation Table - Use Case 1 & 2 */}
                <div className="card" style={{ flexGrow: 2 }}>
                  <h3 className="card-title">Region & Category Aggregations</h3>
                  <div className="table-container">
                    <table className="corporate-table">
                      <thead>
                        <tr>
                          <th>Region</th>
                          <th>Product Category</th>
                          <th style={{ textAlign: 'right' }}>Total Sales (USD)</th>
                          <th style={{ textAlign: 'right' }}>Top Regional Category</th>
                        </tr>
                      </thead>
                      <tbody>
                        {Object.entries(data.regionCategorySales).flatMap(([region, categories]) => {
                          const catEntries = Object.entries(categories);
                          return catEntries.map(([category, amount], idx) => {
                            const isTopCategory = data.topCategoriesPerRegion[region] === category;
                            return (
                              <tr key={`${region}-${category}`}>
                                {idx === 0 ? (
                                  <td 
                                    rowSpan={catEntries.length} 
                                    style={{ fontWeight: '600', borderRight: '1px solid var(--border-color)', verticalAlign: 'top' }}
                                  >
                                    {region}
                                    <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', fontWeight: '400', marginTop: '4px' }}>
                                      Total: {formatUSD(data.regionalTotals[region])}
                                    </div>
                                  </td>
                                ) : null}
                                <td>{category}</td>
                                <td style={{ textAlign: 'right', fontWeight: '500' }}>{formatUSD(amount)}</td>
                                <td style={{ textAlign: 'right' }}>
                                  {isTopCategory ? (
                                    <span style={{ fontSize: '0.75rem', padding: '2px 6px', backgroundColor: 'rgba(25, 135, 84, 0.15)', color: 'var(--success-color)', borderRadius: '2px', fontWeight: '600' }}>
                                      🏆 Top Category
                                    </span>
                                  ) : '-'}
                                </td>
                              </tr>
                            );
                          });
                        })}
                      </tbody>
                    </table>
                  </div>
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
            </>
          )}
        </div>
      </div>
    </div>
  );
}
