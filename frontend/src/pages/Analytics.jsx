import React, { useEffect, useState, useContext } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';
import { AuthContext } from '../context/AuthContext';
import Sidebar from '../components/Sidebar';
import Header from '../components/Header';
import EmptyState from '../components/EmptyState';
import {
  ResponsiveContainer,
  AreaChart,
  Area,
  BarChart,
  Bar,
  PieChart,
  Pie,
  Cell,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend
} from 'recharts';

export default function Analytics() {
  const [summaryData, setSummaryData] = useState(null);
  const [timelineData, setTimelineData] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [activeTab, setActiveTab] = useState('overall'); // 'overall' | 'regional' | 'category'
  const { logout } = useContext(AuthContext);
  const navigate = useNavigate();

  // Premium neon/modern colors for charts
  const NEON_COLORS = [
    '#6366F1', // Indigo
    '#10B981', // Emerald
    '#3B82F6', // Blue
    '#EC4899', // Pink
    '#F59E0B', // Amber
    '#8B5CF6', // Purple
    '#14B8A6', // Teal
    '#EF4444'  // Red
  ];

  const fetchAnalyticsData = async () => {
    setLoading(true);
    setError('');
    try {
      // Fetch report summary containing nested rollup maps and KPIs
      const summaryRes = await api.get('/api/reports/summary');
      setSummaryData(summaryRes.data);

      // Fetch transaction list (large size) to construct time series line/area charts
      const timelineRes = await api.get('/api/transactions?size=1000&sortBy=transactionDate&direction=asc');
      if (timelineRes.data && timelineRes.data.content) {
        processTimelineData(timelineRes.data.content);
      }
    } catch (err) {
      console.error(err);
      if (err.response?.status === 401) {
        logout();
      } else {
        setError('Failed to load visual business intelligence analytics.');
      }
    } finally {
      setLoading(false);
    }
  };

  const processTimelineData = (transactions) => {
    // Group transactions by Year-Month (e.g. "2026-07") to show trend
    const monthlyGroups = {};
    transactions.forEach(t => {
      if (!t.transactionDate) return;
      const dateStr = t.transactionDate.substring(0, 7); // YYYY-MM
      monthlyGroups[dateStr] = (monthlyGroups[dateStr] || 0) + (t.amount || 0);
    });

    const formattedTimeline = Object.keys(monthlyGroups)
      .sort()
      .map(month => {
        // Format Month String e.g. "2026-07" to "Jul 2026"
        const [year, m] = month.split('-');
        const date = new Date(parseInt(year), parseInt(m) - 1, 1);
        const label = date.toLocaleDateString('en-US', { month: 'short', year: 'numeric' });
        return {
          month: label,
          revenue: monthlyGroups[month]
        };
      });

    setTimelineData(formattedTimeline);
  };

  useEffect(() => {
    fetchAnalyticsData();
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
          <Header title="BI Market Analytics" />
          <div className="content-body" style={{ justifyContent: 'center', alignItems: 'center' }}>
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '15px' }}>
              <div className="spinner" style={{ width: '40px', height: '40px', border: '4px solid var(--border-color)', borderTop: '4px solid var(--primary-btn)' }}></div>
              <div style={{ fontWeight: '600', color: 'var(--text-secondary)' }}>Compiling sales intelligence context...</div>
            </div>
          </div>
        </div>
      </div>
    );
  }

  const hasData = summaryData && summaryData.totalTransactions > 0;

  // Prepare Regional Data for Recharts
  const regionalChartData = summaryData && summaryData.regionalTotals 
    ? Object.keys(summaryData.regionalTotals).map(region => ({
        name: region,
        value: summaryData.regionalTotals[region] / 100.0 // Cents to USD
      })).sort((a, b) => b.value - a.value)
    : [];

  // Prepare Category Data for Recharts
  const categoryChartData = summaryData && summaryData.categoryTotals
    ? Object.keys(summaryData.categoryTotals).map(cat => ({
        name: cat,
        value: summaryData.categoryTotals[cat] / 100.0 // Cents to USD
      })).sort((a, b) => b.value - a.value)
    : [];

  // Prepare Stacked Region-Category data
  const stackedChartData = [];
  if (summaryData && summaryData.regionCategorySales) {
    Object.keys(summaryData.regionCategorySales).forEach(region => {
      const row = { name: region };
      const categories = summaryData.regionCategorySales[region];
      Object.keys(categories).forEach(cat => {
        row[cat] = categories[cat] / 100.0;
      });
      stackedChartData.push(row);
    });
  }

  // Get list of all categories to draw stack components
  const allCategories = summaryData && summaryData.categoryTotals 
    ? Object.keys(summaryData.categoryTotals) 
    : [];

  return (
    <div className="app-container">
      <Sidebar />
      <div className="main-wrapper">
        <Header title="BI Market Analytics" />

        <div className="content-body">
          {error && <div className="alert alert-danger">{error}</div>}

          {!hasData ? (
            <EmptyState 
              title="No regional statistics available." 
              description="Import transaction data to generate BI charts."
              actionText="Import Data"
              onAction={() => navigate('/transactions')}
            />
          ) : (
            <>
              {/* Premium Tab Navigation */}
              <div style={{ display: 'flex', gap: '10px', borderBottom: '1px solid var(--border-color)', paddingBottom: '10px' }}>
                <button 
                  className={`btn ${activeTab === 'overall' ? 'btn-primary' : 'btn-secondary'}`} 
                  onClick={() => setActiveTab('overall')}
                  style={{ borderRadius: '20px', padding: '8px 20px', fontSize: '0.85rem' }}
                >
                  🌐 Overall View
                </button>
                <button 
                  className={`btn ${activeTab === 'regional' ? 'btn-primary' : 'btn-secondary'}`} 
                  onClick={() => setActiveTab('regional')}
                  style={{ borderRadius: '20px', padding: '8px 20px', fontSize: '0.85rem' }}
                >
                  🗺️ Regional Insights
                </button>
                <button 
                  className={`btn ${activeTab === 'category' ? 'btn-primary' : 'btn-secondary'}`} 
                  onClick={() => setActiveTab('category')}
                  style={{ borderRadius: '20px', padding: '8px 20px', fontSize: '0.85rem' }}
                >
                  📦 Product Distributions
                </button>
              </div>

              {/* KPI Section */}
              <div className="grid-kpi">
                <div className="card kpi-card">
                  <span className="kpi-label">Market Revenue</span>
                  <span className="kpi-value">{formatUSD(summaryData.totalRevenue / 100.0)}</span>
                  <span className="kpi-subtext">Sum of all regions</span>
                </div>
                <div className="card kpi-card">
                  <span className="kpi-label">Total Transactions</span>
                  <span className="kpi-value">{summaryData.totalTransactions.toLocaleString()}</span>
                  <span className="kpi-subtext">Validated ledger entries</span>
                </div>
                <div className="card kpi-card">
                  <span className="kpi-label">Average Order Value</span>
                  <span className="kpi-value">{formatUSD(summaryData.averageTransactionValue / 100.0)}</span>
                  <span className="kpi-subtext">AOV per invoice</span>
                </div>
                <div className="card kpi-card">
                  <span className="kpi-label">Best Territory</span>
                  <span className="kpi-value" style={{ fontSize: '1.45rem', fontWeight: '800', wordBreak: 'break-all' }}>
                    {summaryData.topRegionName || 'N/A'}
                  </span>
                  <span className="kpi-subtext">Highest sales contributor</span>
                </div>
              </div>

              {/* Tabs Content */}
              {activeTab === 'overall' && (
                <>
                  <div className="grid-charts">
                    {/* Time Series Area Chart */}
                    <div className="card" style={{ gridColumn: 'span 2' }}>
                      <h3 className="card-title" style={{ border: 'none', marginBottom: '10px' }}>Revenue Growth Timeline</h3>
                      <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginBottom: '20px' }}>Monthly gross revenue progression trends</p>
                      <div style={{ height: '350px', width: '100%' }}>
                        {timelineData.length > 0 ? (
                          <ResponsiveContainer width="100%" height="100%">
                            <AreaChart data={timelineData} margin={{ top: 10, right: 30, left: 20, bottom: 0 }}>
                              <defs>
                                <linearGradient id="colorRevenue" x1="0" y1="0" x2="0" y2="1">
                                  <stop offset="5%" stopColor="#6366F1" stopOpacity={0.4}/>
                                  <stop offset="95%" stopColor="#6366F1" stopOpacity={0.0}/>
                                </linearGradient>
                              </defs>
                              <CartesianGrid strokeDasharray="3 3" stroke="var(--border-color)" />
                              <XAxis dataKey="month" stroke="var(--text-secondary)" fontSize={12} />
                              <YAxis 
                                stroke="var(--text-secondary)" 
                                fontSize={12}
                                tickFormatter={(val) => `$${(val / 1000).toFixed(0)}k`} 
                              />
                              <Tooltip 
                                formatter={(val) => [formatUSD(val), 'Revenue']}
                                contentStyle={{ backgroundColor: 'var(--card-bg)', borderColor: 'var(--border-color)', borderRadius: '8px', color: 'var(--text-primary)' }}
                              />
                              <Area type="monotone" dataKey="revenue" stroke="#6366F1" strokeWidth={3} fillOpacity={1} fill="url(#colorRevenue)" />
                            </AreaChart>
                          </ResponsiveContainer>
                        ) : (
                          <div style={{ display: 'flex', height: '100%', alignItems: 'center', justifyContent: 'center', color: 'var(--text-secondary)' }}>
                            No timeline data points to render. Try importing a dataset with varying timestamps.
                          </div>
                        )}
                      </div>
                    </div>
                  </div>

                  <div className="grid-charts">
                    {/* Regional Performance Shares */}
                    <div className="card">
                      <h3 className="card-title">Geographic Sales Share</h3>
                      <div style={{ height: '320px', width: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                        <ResponsiveContainer width="100%" height="100%">
                          <PieChart>
                            <Pie
                              data={regionalChartData}
                              cx="50%"
                              cy="50%"
                              innerRadius={70}
                              outerRadius={100}
                              paddingAngle={4}
                              dataKey="value"
                            >
                              {regionalChartData.map((entry, index) => (
                                <Cell key={`cell-${index}`} fill={NEON_COLORS[index % NEON_COLORS.length]} />
                              ))}
                            </Pie>
                            <Tooltip formatter={(val) => formatUSD(val)} />
                            <Legend layout="horizontal" verticalAlign="bottom" align="center" wrapperStyle={{ fontSize: '11px' }} />
                          </PieChart>
                        </ResponsiveContainer>
                      </div>
                    </div>

                    {/* Product Category Shares */}
                    <div className="card">
                      <h3 className="card-title">Product Category Volume Share</h3>
                      <div style={{ height: '320px', width: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                        <ResponsiveContainer width="100%" height="100%">
                          <PieChart>
                            <Pie
                              data={categoryChartData}
                              cx="50%"
                              cy="50%"
                              innerRadius={0}
                              outerRadius={95}
                              dataKey="value"
                              labelLine={false}
                              label={({ name, percent }) => `${name} ${(percent * 100).toFixed(0)}%`}
                              style={{ fontSize: '11px', fontWeight: '600' }}
                            >
                              {categoryChartData.map((entry, index) => (
                                <Cell key={`cell-${index}`} fill={NEON_COLORS[(index + 3) % NEON_COLORS.length]} />
                              ))}
                            </Pie>
                            <Tooltip formatter={(val) => formatUSD(val)} />
                          </PieChart>
                        </ResponsiveContainer>
                      </div>
                    </div>
                  </div>
                </>
              )}

              {activeTab === 'regional' && (
                <>
                  <div className="grid-charts">
                    {/* Nested Map Stacked Bar Chart */}
                    <div className="card" style={{ gridColumn: 'span 2' }}>
                      <h3 className="card-title" style={{ border: 'none', marginBottom: '10px' }}>Region & Category Rollup Matrix</h3>
                      <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginBottom: '20px' }}>Nested-map representation of categories within territories</p>
                      <div style={{ height: '400px', width: '100%' }}>
                        <ResponsiveContainer width="100%" height="100%">
                          <BarChart data={stackedChartData} margin={{ top: 20, right: 30, left: 20, bottom: 5 }}>
                            <CartesianGrid strokeDasharray="3 3" stroke="var(--border-color)" />
                            <XAxis dataKey="name" stroke="var(--text-secondary)" fontSize={12} />
                            <YAxis 
                              stroke="var(--text-secondary)" 
                              fontSize={12} 
                              tickFormatter={(val) => `$${(val / 1000).toFixed(0)}k`} 
                            />
                            <Tooltip 
                              formatter={(val) => formatUSD(val)}
                              contentStyle={{ backgroundColor: 'var(--card-bg)', borderColor: 'var(--border-color)', borderRadius: '8px', color: 'var(--text-primary)' }}
                            />
                            <Legend wrapperStyle={{ fontSize: '11px', paddingTop: '10px' }} />
                            {allCategories.map((cat, idx) => (
                              <Bar 
                                key={cat} 
                                dataKey={cat} 
                                stackId="a" 
                                fill={NEON_COLORS[idx % NEON_COLORS.length]} 
                                radius={[idx === allCategories.length - 1 ? 4 : 0, idx === allCategories.length - 1 ? 4 : 0, 0, 0]}
                              />
                            ))}
                          </BarChart>
                        </ResponsiveContainer>
                      </div>
                    </div>
                  </div>

                  <div className="grid-charts">
                    {/* Regional Ranking Card */}
                    <div className="card" style={{ gridColumn: 'span 2' }}>
                      <h3 className="card-title">Territory Revenue Rankings</h3>
                      <div style={{ height: '350px', width: '100%' }}>
                        <ResponsiveContainer width="100%" height="100%">
                          <BarChart data={regionalChartData} layout="vertical" margin={{ top: 5, right: 30, left: 40, bottom: 5 }}>
                            <CartesianGrid strokeDasharray="3 3" stroke="var(--border-color)" />
                            <XAxis type="number" stroke="var(--text-secondary)" fontSize={12} tickFormatter={(val) => `$${(val / 1000).toFixed(0)}k`} />
                            <YAxis dataKey="name" type="category" stroke="var(--text-secondary)" fontSize={12} />
                            <Tooltip formatter={(val) => formatUSD(val)} />
                            <Bar dataKey="value" fill="#3B82F6" radius={[0, 8, 8, 0]} barSize={25} />
                          </BarChart>
                        </ResponsiveContainer>
                      </div>
                    </div>
                  </div>
                </>
              )}

              {activeTab === 'category' && (
                <>
                  <div className="grid-charts">
                    {/* Category Rankings Bar Chart */}
                    <div className="card" style={{ gridColumn: 'span 2' }}>
                      <h3 className="card-title">Category Revenue Contribution</h3>
                      <div style={{ height: '380px', width: '100%' }}>
                        <ResponsiveContainer width="100%" height="100%">
                          <BarChart data={categoryChartData} margin={{ top: 20, right: 30, left: 20, bottom: 5 }}>
                            <CartesianGrid strokeDasharray="3 3" stroke="var(--border-color)" />
                            <XAxis dataKey="name" stroke="var(--text-secondary)" fontSize={12} />
                            <YAxis stroke="var(--text-secondary)" fontSize={12} tickFormatter={(val) => `$${(val / 1000).toFixed(0)}k`} />
                            <Tooltip formatter={(val) => formatUSD(val)} />
                            <Bar dataKey="value" fill="#10B981" radius={[8, 8, 0, 0]} barSize={40}>
                              {categoryChartData.map((entry, index) => (
                                <Cell key={`cell-${index}`} fill={NEON_COLORS[(index + 2) % NEON_COLORS.length]} />
                              ))}
                            </Bar>
                          </BarChart>
                        </ResponsiveContainer>
                      </div>
                    </div>
                  </div>

                  <div className="grid-charts">
                    {/* Category KPI details */}
                    <div className="card" style={{ gridColumn: 'span 2' }}>
                      <h3 className="card-title">Category Distribution Details</h3>
                      <div className="table-container">
                        <table className="corporate-table">
                          <thead>
                            <tr>
                              <th>Product Category</th>
                              <th style={{ textAlign: 'right' }}>Total Revenue</th>
                              <th style={{ textAlign: 'right' }}>Top Territory Performance</th>
                            </tr>
                          </thead>
                          <tbody>
                            {categoryChartData.map((c) => {
                              // Find top region for this category
                              let topReg = 'N/A';
                              let topRegSales = 0;
                              if (summaryData.regionCategorySales) {
                                Object.keys(summaryData.regionCategorySales).forEach(region => {
                                  const cats = summaryData.regionCategorySales[region];
                                  if (cats[c.name] && cats[c.name] > topRegSales) {
                                    topRegSales = cats[c.name];
                                    topReg = region;
                                  }
                                });
                              }

                              return (
                                <tr key={c.name}>
                                  <td style={{ fontWeight: '700' }}>{c.name}</td>
                                  <td style={{ textAlign: 'right', fontWeight: '600', color: 'var(--primary-btn)' }}>
                                    {formatUSD(c.value)}
                                  </td>
                                  <td style={{ textAlign: 'right' }}>
                                    <span className="user-badge" style={{ backgroundColor: 'var(--success-bg)', color: 'var(--success-color)', borderColor: 'transparent' }}>
                                      {topReg} ({formatUSD(topRegSales / 100.0)})
                                    </span>
                                  </td>
                                </tr>
                              );
                            })}
                          </tbody>
                        </table>
                      </div>
                    </div>
                  </div>
                </>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}
