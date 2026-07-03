import React, { useEffect, useState, useContext } from 'react';
import api from '../services/api';
import { AuthContext } from '../context/AuthContext';
import Sidebar from '../components/Sidebar';
import Header from '../components/Header';
import Modal from '../components/Modal';

export default function Transactions() {
  const { user } = useContext(AuthContext);

  // Lists & Page State
  const [transactions, setTransactions] = useState([]);
  const [regions, setRegions] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(false);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  // Pagination & Sorting Params
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [sortBy, setSortBy] = useState('transactionDate');
  const [direction, setDirection] = useState('desc');

  // Filters State
  const [search, setSearch] = useState('');
  const [selectedRegion, setSelectedRegion] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [minAmount, setMinAmount] = useState('');
  const [maxAmount, setMaxAmount] = useState('');

  // Modals & Form State
  const [isEntryModalOpen, setIsEntryModalOpen] = useState(false);
  const [isEditing, setIsEditing] = useState(false);
  const [editingId, setEditingId] = useState(null);
  
  const [formCode, setFormCode] = useState('');
  const [formDate, setFormDate] = useState('');
  const [formRegion, setFormRegion] = useState('');
  const [formCategory, setFormCategory] = useState('');
  const [formAmount, setFormAmount] = useState('');
  const [formError, setFormError] = useState('');

  // Import State
  const [isImportModalOpen, setIsImportModalOpen] = useState(false);
  const [uploadFile, setUploadFile] = useState(null);
  const [importResult, setImportResult] = useState(null);
  const [importError, setImportError] = useState('');
  const [importing, setImporting] = useState(false);
  const [duplicateAction, setDuplicateAction] = useState('SKIP');
  const [missingRegionPolicy, setMissingRegionPolicy] = useState('ASSIGN_UNKNOWN');
  const [missingCategoryPolicy, setMissingCategoryPolicy] = useState('CREATE_AUTOMATIC');
  const [etlStep, setEtlStep] = useState(0); 
  const [activeTab, setActiveTab] = useState('ledger'); 
  const [importHistory, setImportHistory] = useState([]);

  // Feedback State
  const [alertMsg, setAlertMsg] = useState({ type: '', text: '' });

  // Permissions Helpers
  const isViewer = user?.role === 'ROLE_VIEWER';
  const isAdmin = user?.role === 'ROLE_ADMINISTRATOR';
  const canModify = user?.role === 'ROLE_ADMINISTRATOR' || 
                    user?.role === 'ROLE_BUSINESS_ANALYST' || 
                    user?.role === 'ROLE_REGIONAL_MANAGER';

  const fetchFiltersOptions = async () => {
    try {
      const [regionsRes, categoriesRes] = await Promise.all([
        api.get('/api/regions'),
        api.get('/api/categories')
      ]);
      setRegions(regionsRes.data);
      setCategories(categoriesRes.data);
    } catch (err) {
      console.error("Failed to load filter options", err);
    }
  };

  const fetchTransactions = async () => {
    setLoading(true);
    try {
      const params = {
        page,
        size,
        sortBy,
        direction,
        search: search.trim() || undefined,
        region: selectedRegion || undefined,
        category: selectedCategory || undefined,
        startDate: startDate || undefined,
        endDate: endDate || undefined,
        minAmount: minAmount || undefined,
        maxAmount: maxAmount || undefined
      };

      const response = await api.get('/api/transactions', { params });
      setTransactions(response.data.content);
      setTotalElements(response.data.totalElements);
      setTotalPages(response.data.totalPages);
    } catch (err) {
      console.error(err);
      triggerAlert('danger', 'Failed to retrieve transactions list.');
    } finally {
      setLoading(false);
    }
  };

  const fetchImportHistory = async () => {
    try {
      const response = await api.get('/api/transactions/import/history');
      setImportHistory(response.data);
    } catch (err) {
      console.error("Failed to retrieve import logs audit history", err);
    }
  };

  useEffect(() => {
    fetchTransactions();
    fetchImportHistory();
  }, [page, size, sortBy, direction]);

  useEffect(() => {
    fetchFiltersOptions();
  }, [transactions]);

  const triggerAlert = (type, text) => {
    setAlertMsg({ type, text });
    setTimeout(() => setAlertMsg({ type: '', text: '' }), 5000);
  };

  const handleApplyFilters = (e) => {
    e.preventDefault();
    setPage(0);
    fetchTransactions();
  };

  const handleResetFilters = () => {
    setSearch('');
    setSelectedRegion('');
    setSelectedCategory('');
    setStartDate('');
    setEndDate('');
    setMinAmount('');
    setMaxAmount('');
    setPage(0);
    // Timeout to let states clear before fetching
    setTimeout(fetchTransactions, 50);
  };

  const handleSort = (field) => {
    if (sortBy === field) {
      setDirection(direction === 'asc' ? 'desc' : 'asc');
    } else {
      setSortBy(field);
      setDirection('desc');
    }
    setPage(0);
  };

  const openAddModal = () => {
    setIsEditing(false);
    setEditingId(null);
    setFormCode('');
    setFormDate(new Date().toISOString().split('T')[0]);
    setFormRegion('');
    setFormCategory('');
    setFormAmount('');
    setFormError('');
    setIsEntryModalOpen(true);
  };

  const openEditModal = (tx) => {
    setIsEditing(true);
    setEditingId(tx.id);
    setFormCode(tx.transactionCode);
    setFormDate(tx.transactionDate);
    setFormRegion(tx.regionName);
    setFormCategory(tx.categoryName);
    setFormAmount(tx.amount);
    setFormError('');
    setIsEntryModalOpen(true);
  };

  const handleFormSubmit = async (e) => {
    e.preventDefault();
    setFormError('');

    if (!formCode || !formDate || !formRegion || !formCategory || !formAmount) {
      setFormError('Please fill in all input fields.');
      return;
    }

    const payload = {
      transactionCode: formCode,
      transactionDate: formDate,
      regionName: formRegion,
      categoryName: formCategory,
      amount: parseFloat(formAmount)
    };

    try {
      if (isEditing) {
        await api.put(`/api/transactions/${editingId}`, payload);
        triggerAlert('success', `Transaction ${formCode} updated successfully.`);
      } else {
        await api.post('/api/transactions', payload);
        triggerAlert('success', `Transaction ${formCode} created successfully.`);
      }
      setIsEntryModalOpen(false);
      fetchTransactions();
    } catch (err) {
      console.error(err);
      setFormError(err.response?.data?.message || 'Failed to submit transaction details. Verify code uniqueness.');
    }
  };

  const handleDelete = async (id, code) => {
    if (window.confirm(`Are you sure you want to permanently delete transaction: ${code}?`)) {
      try {
        await api.delete(`/api/transactions/${id}`);
        triggerAlert('success', `Transaction ${code} has been deleted.`);
        fetchTransactions();
      } catch (err) {
        console.error(err);
        triggerAlert('danger', err.response?.data?.message || 'Delete operation denied.');
      }
    }
  };

  const handleCsvUpload = async (e) => {
    e.preventDefault();
    setImportError('');
    setImportResult(null);

    if (!uploadFile) {
      setImportError('Please select a CSV file.');
      return;
    }

    const formData = new FormData();
    formData.append('file', uploadFile);

    setImporting(true);
    setEtlStep(1); // Stage 1: Reading File...

    const t1 = setTimeout(() => setEtlStep(2), 400);  // Stage 2: Mapping Columns...
    const t2 = setTimeout(() => setEtlStep(3), 850);  // Stage 3: Validating Data...
    const t3 = setTimeout(() => setEtlStep(4), 1400); // Stage 4: Checking Duplicates...
    const t4 = setTimeout(() => setEtlStep(5), 2000); // Stage 5: Saving Transactions...

    try {
      const response = await api.post(
        `/api/transactions/import?duplicateAction=${duplicateAction}&missingRegionPolicy=${missingRegionPolicy}&missingCategoryPolicy=${missingCategoryPolicy}`, 
        formData, 
        {
          headers: {
            'Content-Type': 'multipart/form-data'
          }
        }
      );
      
      setImportResult(response.data);
      const status = response.data.status;

      if (status === 'Completed Successfully') {
        triggerAlert('success', `ETL Pipeline Success: Imported ${response.data.importedRecords} rows.`);
      } else if (status === 'Completed With Warnings') {
        triggerAlert('warning', `Import finished with warnings. Skipped ${response.data.duplicatesSkipped} duplicates.`);
      } else if (status === 'Partial Success') {
        triggerAlert('warning', `Ingested ${response.data.importedRecords + response.data.updatedRecords} records, failed ${response.data.failedRecords} rows.`);
      } else {
        triggerAlert('danger', `Import Pipeline Failed: all records rejected.`);
      }
      
      fetchTransactions();
      fetchImportHistory();
    } catch (err) {
      console.error(err);
      setImportError(err.response?.data?.message || 'Failed to process CSV import ingestion pipeline.');
    } finally {
      clearTimeout(t1);
      clearTimeout(t2);
      clearTimeout(t3);
      clearTimeout(t4);
      setEtlStep(6); // Completed.
      setImporting(false);
    }
  };

  const downloadErrorLog = () => {
    if (!importResult || !importResult.errors || importResult.errors.length === 0) return;
    
    let logContent = `=========================================================================================================\n`;
    logContent += `                              SALESSPHERE BI - DATA QUALITY PIPELINE ERROR REPORT\n`;
    logContent += `=========================================================================================================\n`;
    logContent += `Timestamp        : ${new Date().toLocaleString()}\n`;
    logContent += `File Name        : ${uploadFile?.name || 'Unknown'}\n`;
    logContent += `Duplicate Policy : ${duplicateAction}\n`;
    logContent += `Region Policy    : ${missingRegionPolicy}\n`;
    logContent += `Category Policy  : ${missingCategoryPolicy}\n`;
    logContent += `=========================================================================================================\n\n`;
    logContent += `Row Number | Transaction Code | Field | Original Value | Reason | Suggested Fix | Severity\n`;
    logContent += `---------------------------------------------------------------------------------------------------------\n`;
    
    importResult.errors.forEach(err => {
      const lineNum = err.lineNumber || '-';
      const txCode = err.transactionCode || '-';
      const targetField = err.field || 'Ingestion Pipeline';
      const origVal = err.originalValue ? err.originalValue.substring(0, 30).trim() : '-';
      const reason = err.errorMessage || '-';
      const fix = err.suggestedFix || 'Check file formatting.';
      const sev = err.severity || 'ERROR';
      
      logContent += `${lineNum} | ${txCode} | ${targetField} | ${origVal} | ${reason} | ${fix} | ${sev}\n`;
    });
    
    const blob = new Blob([logContent], { type: 'text/plain;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', `data_quality_error_report_${Date.now()}.txt`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const downloadProcessingReport = () => {
    if (!importResult) return;
    
    let logContent = `======================================================================\n`;
    logContent += `           SALESSPHERE BI - ENTERPRISE ETL PROCESSING REPORT\n`;
    logContent += `======================================================================\n`;
    logContent += `Report Generated: ${new Date().toLocaleString()}\n`;
    logContent += `Input File Name : ${uploadFile?.name || 'Unknown'}\n`;
    logContent += `File Encoding   : ${importResult.detectedEncoding || 'Auto-Detected'}\n`;
    logContent += `Field Delimiter : ${importResult.detectedDelimiter || 'Auto-Detected'}\n`;
    logContent += `Duplicate Policy: ${duplicateAction}\n`;
    logContent += `Region Policy   : ${missingRegionPolicy}\n`;
    logContent += `Category Policy : ${missingCategoryPolicy}\n`;
    logContent += `----------------------------------------------------------------------\n`;
    logContent += `ETL PIPELINE METRICS:\n`;
    logContent += `----------------------------------------------------------------------\n`;
    logContent += `Total Rows Parsed    : ${importResult.totalRecords}\n`;
    logContent += `Successfully Imported: ${importResult.importedRecords}\n`;
    logContent += `Successfully Updated : ${importResult.updatedRecords || 0}\n`;
    logContent += `Duplicates Skipped   : ${importResult.duplicatesSkipped || 0}\n`;
    logContent += `Duplicates Updated   : ${importResult.duplicatesUpdated || 0}\n`;
    logContent += `Validation Errors    : ${importResult.validationErrors || 0}\n`;
    logContent += `Parsing Errors       : ${importResult.parsingErrors || 0}\n`;
    logContent += `Warnings             : ${importResult.warnings || 0}\n`;
    logContent += `Ignored Rows         : ${importResult.ignoredRows || 0}\n`;
    logContent += `Execution Time       : ${importResult.processingTimeMs} ms\n`;
    logContent += `Throughput Speed     : ${importResult.averageSpeedRecordsPerSec} rows/second\n`;
    logContent += `----------------------------------------------------------------------\n`;
    logContent += `DIMENSIONS AND COLUMN MAPPING:\n`;
    logContent += `----------------------------------------------------------------------\n`;
    logContent += `Ignored Columns Count: ${importResult.ignoredColumnsCount}\n`;
    if (importResult.ignoredColumnsCount > 0) {
      logContent += `Ignored Columns List : ${importResult.ignoredColumns.join(', ')}\n`;
    } else {
      logContent += `Ignored Columns List : None (All columns mapped)\n`;
    }
    logContent += `Pipeline Resolution  : ${importResult.status}\n`;
    logContent += `======================================================================\n`;
    
    const blob = new Blob([logContent], { type: 'text/plain;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', `etl_processing_report_${Date.now()}.txt`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const formatUSD = (cents) => {
    return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(cents / 100);
  };

  return (
    <div className="app-container">
      <Sidebar />
      <div className="main-wrapper">
        <Header title="Transaction Ledger" />

        <div className="content-body">
          {alertMsg.text && (
            <div className={`alert alert-${alertMsg.type}`}>
              {alertMsg.text}
            </div>
          )}

          {/* Tab Selection Header */}
          <div style={{ display: 'flex', gap: '15px', borderBottom: '1px solid var(--border-color)', marginBottom: '20px' }}>
            <button 
              type="button"
              onClick={() => setActiveTab('ledger')}
              style={{
                background: 'none',
                border: 'none',
                borderBottom: activeTab === 'ledger' ? '2px solid var(--primary-color)' : 'none',
                color: activeTab === 'ledger' ? 'var(--primary-color)' : 'var(--text-secondary)',
                fontWeight: activeTab === 'ledger' ? 'bold' : 'normal',
                padding: '10px 15px',
                cursor: 'pointer',
                fontSize: '0.9rem'
              }}
            >
              📄 Transaction Ledger
            </button>
            <button 
              type="button"
              onClick={() => setActiveTab('history')}
              style={{
                background: 'none',
                border: 'none',
                borderBottom: activeTab === 'history' ? '2px solid var(--primary-color)' : 'none',
                color: activeTab === 'history' ? 'var(--primary-color)' : 'var(--text-secondary)',
                fontWeight: activeTab === 'history' ? 'bold' : 'normal',
                padding: '10px 15px',
                cursor: 'pointer',
                fontSize: '0.9rem'
              }}
            >
              📊 Ingestion History & ETL Audits
            </button>
          </div>

          {activeTab === 'history' ? (
            <div className="card" style={{ padding: '20px' }}>
              <h3 style={{ fontSize: '1rem', marginTop: '0', marginBottom: '15px' }}>Data Quality Pipeline & Ingestion History Audits</h3>
              <div className="table-container">
                <table className="corporate-table">
                  <thead>
                    <tr>
                      <th>Timestamp</th>
                      <th>User Session</th>
                      <th>Status</th>
                      <th style={{ textAlign: 'right' }}>Imported Rows</th>
                      <th style={{ textAlign: 'right' }}>Updated Rows</th>
                      <th style={{ textAlign: 'right' }}>Duplicates Handled</th>
                      <th style={{ textAlign: 'right' }}>Failed Rows</th>
                      <th style={{ textAlign: 'right' }}>Time Elapsed (ms)</th>
                    </tr>
                  </thead>
                  <tbody>
                    {importHistory.length === 0 ? (
                      <tr>
                        <td colSpan={8} style={{ textAlign: 'center', padding: '30px', color: 'var(--text-secondary)' }}>
                          No historical data processing pipeline audits available.
                        </td>
                      </tr>
                    ) : (
                      importHistory.map((h) => (
                        <tr key={h.id}>
                          <td style={{ fontWeight: '500' }}>{new Date(h.timestamp).toLocaleString()}</td>
                          <td>{h.username}</td>
                          <td>
                            <span style={{
                              backgroundColor: h.status === 'Completed Successfully' ? '#10b981' : (h.status === 'Completed With Warnings' || h.status === 'Partial Success' ? '#f59e0b' : '#ef4444'),
                              color: '#fff',
                              padding: '2px 6px',
                              borderRadius: '4px',
                              fontSize: '0.75rem',
                              fontWeight: 'bold'
                            }}>
                              {h.status}
                            </span>
                          </td>
                          <td style={{ textAlign: 'right', fontWeight: '500', color: '#10b981' }}>{h.rowsImported}</td>
                          <td style={{ textAlign: 'right', fontWeight: '500', color: 'var(--primary-color)' }}>{h.rowsUpdated}</td>
                          <td style={{ textAlign: 'right', fontWeight: '500', color: 'var(--text-secondary)' }}>{h.duplicates}</td>
                          <td style={{ textAlign: 'right', fontWeight: '500', color: '#ef4444' }}>{h.rowsFailed}</td>
                          <td style={{ textAlign: 'right' }}>{h.processingTimeMs} ms</td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          ) : (
            <>
              {/* Action Row */}
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div style={{ color: 'var(--text-secondary)', fontSize: '0.9rem' }}>
                  Showing {transactions.length} of {totalElements} transaction entries.
                </div>
                
                {canModify && (
                  <div style={{ display: 'flex', gap: '10px' }}>
                    <button className="btn btn-secondary" onClick={() => { setImportResult(null); setUploadFile(null); setImportError(''); setIsImportModalOpen(true); }}>
                      📥 Bulk Import CSV
                    </button>
                    <button className="btn btn-primary" onClick={openAddModal}>
                      ➕ Add Transaction
                    </button>
                  </div>
                )}
              </div>

              {/* Filter Panel */}
              <form className="filter-row" onSubmit={handleApplyFilters}>
                <div className="filter-item" style={{ flexGrow: 1.5 }}>
                  <label>Search Code/Metadata</label>
                  <input 
                    type="text" 
                    className="form-input" 
                    placeholder="Search..."
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                  />
                </div>
                <div className="filter-item">
                  <label>Region</label>
                  <select className="form-select" value={selectedRegion} onChange={(e) => setSelectedRegion(e.target.value)}>
                    <option value="">All Regions</option>
                    {regions.map(r => <option key={r.id} value={r.name}>{r.name}</option>)}
                  </select>
                </div>
                <div className="filter-item">
                  <label>Category</label>
                  <select className="form-select" value={selectedCategory} onChange={(e) => setSelectedCategory(e.target.value)}>
                    <option value="">All Categories</option>
                    {categories.map(c => <option key={c.id} value={c.name}>{c.name}</option>)}
                  </select>
                </div>
                <div className="filter-item">
                  <label>Start Date</label>
                  <input type="date" className="form-input" value={startDate} onChange={(e) => setStartDate(e.target.value)} />
                </div>
                <div className="filter-item">
                  <label>End Date</label>
                  <input type="date" className="form-input" value={endDate} onChange={(e) => setEndDate(e.target.value)} />
                </div>
                <div className="filter-item" style={{ minWidth: '90px' }}>
                  <label>Min ($)</label>
                  <input type="number" className="form-input" min="0" placeholder="Min" value={minAmount} onChange={(e) => setMinAmount(e.target.value)} />
                </div>
                <div className="filter-item" style={{ minWidth: '90px' }}>
                  <label>Max ($)</label>
                  <input type="number" className="form-input" min="0" placeholder="Max" value={maxAmount} onChange={(e) => setMaxAmount(e.target.value)} />
                </div>
                <div style={{ display: 'flex', gap: '8px' }}>
                  <button type="submit" className="btn btn-primary">Apply</button>
                  <button type="button" className="btn btn-secondary" onClick={handleResetFilters}>Reset</button>
                </div>
              </form>

              {/* Data List */}
              {transactions.length === 0 && !loading ? (
                <div className="empty-state">
                  <div className="empty-state-icon">📄</div>
                  <h3 className="empty-state-title">No transactions available.</h3>
                  <p className="empty-state-desc">Import transaction data or manually record a sale to populate results.</p>
                </div>
              ) : (
                <div className="card" style={{ padding: '0px', overflow: 'hidden' }}>
                  <div className="table-container">
                    <table className="corporate-table">
                      <thead>
                        <tr>
                          <th style={{ cursor: 'pointer' }} onClick={() => handleSort('transactionCode')}>
                            Code {sortBy === 'transactionCode' && (direction === 'asc' ? '▲' : '▼')}
                          </th>
                          <th style={{ cursor: 'pointer' }} onClick={() => handleSort('transactionDate')}>
                            Date {sortBy === 'transactionDate' && (direction === 'asc' ? '▲' : '▼')}
                          </th>
                          <th>Region</th>
                          <th>Category</th>
                          <th style={{ textAlign: 'right', cursor: 'pointer' }} onClick={() => handleSort('amountCents')}>
                            Amount {sortBy === 'amountCents' && (direction === 'asc' ? '▲' : '▼')}
                          </th>
                          <th>Created By</th>
                          {!isViewer && <th style={{ textAlign: 'right' }}>Actions</th>}
                        </tr>
                      </thead>
                      <tbody>
                        {loading ? (
                          <tr>
                            <td colSpan={7} style={{ textAlign: 'center', padding: '40px', color: 'var(--text-secondary)' }}>
                              Retrieving records from ledger database...
                            </td>
                          </tr>
                        ) : (
                          transactions.map((tx) => (
                            <tr key={tx.id}>
                              <td style={{ fontWeight: '600' }}>{tx.transactionCode}</td>
                              <td>{tx.transactionDate}</td>
                              <td>{tx.regionName}</td>
                              <td>{tx.categoryName}</td>
                              <td style={{ textAlign: 'right', fontWeight: '600' }}>{formatUSD(tx.amountCents)}</td>
                              <td>
                                <span style={{ fontSize: '0.8rem', padding: '2px 6px', background: 'var(--bg-color)', border: '1px solid var(--border-color)', borderRadius: '2px' }}>
                                  {tx.createdByUsername}
                                </span>
                              </td>
                              {!isViewer && (
                                <td style={{ textAlign: 'right' }}>
                                  <div style={{ display: 'inline-flex', gap: '8px' }}>
                                    <button className="btn btn-secondary btn-small" onClick={() => openEditModal(tx)}>
                                      ✏️ Edit
                                    </button>
                                    {isAdmin && (
                                      <button className="btn btn-danger btn-small" onClick={() => handleDelete(tx.id, tx.transactionCode)}>
                                        🗑️ Delete
                                      </button>
                                    )}
                                  </div>
                                </td>
                              )}
                            </tr>
                          ))
                        )}
                      </tbody>
                    </table>
                  </div>

                  {/* Pagination controls */}
                  <div className="pagination" style={{ padding: '20px' }}>
                    <div className="pagination-info">
                      Page <strong>{page + 1}</strong> of <strong>{totalPages || 1}</strong>
                    </div>
                    <div className="pagination-controls">
                      <button className="btn btn-secondary btn-small" disabled={page === 0} onClick={() => setPage(page - 1)}>
                        ◀ Prev
                      </button>
                      <button className="btn btn-secondary btn-small" disabled={page >= totalPages - 1} onClick={() => setPage(page + 1)}>
                        Next ▶
                      </button>
                    </div>
                  </div>
                </div>
              )}
            </>
          )}
        </div>
      </div>

      {/* Manual Entry Modal */}
      <Modal 
        isOpen={isEntryModalOpen} 
        title={isEditing ? `Edit Transaction ${formCode}` : 'Manual Transaction Entry'} 
        onClose={() => setIsEntryModalOpen(false)}
        footer={
          <>
            <button className="btn btn-secondary" onClick={() => setIsEntryModalOpen(false)}>Cancel</button>
            <button className="btn btn-primary" onClick={handleFormSubmit}>Save Transaction</button>
          </>
        }
      >
        {formError && <div className="alert alert-danger" style={{ padding: '8px 12px', fontSize: '0.85rem' }}>{formError}</div>}
        <form onSubmit={handleFormSubmit}>
          <div className="form-group">
            <label className="form-label">Transaction Code</label>
            <input 
              type="text" 
              className="form-input" 
              placeholder="e.g. TXN-10001" 
              value={formCode}
              onChange={(e) => setFormCode(e.target.value)}
              required 
            />
          </div>
          <div className="form-group">
            <label className="form-label">Transaction Date</label>
            <input 
              type="date" 
              className="form-input" 
              value={formDate}
              onChange={(e) => setFormDate(e.target.value)}
              required 
            />
          </div>
          <div className="form-group">
            <label className="form-label">Region Name</label>
            <input 
              type="text" 
              className="form-input" 
              placeholder="e.g. North, South" 
              value={formRegion}
              onChange={(e) => setFormRegion(e.target.value)}
              required 
            />
          </div>
          <div className="form-group">
            <label className="form-label">Category Name</label>
            <input 
              type="text" 
              className="form-input" 
              placeholder="e.g. Electronics, Apparel" 
              value={formCategory}
              onChange={(e) => setFormCategory(e.target.value)}
              required 
            />
          </div>
          <div className="form-group">
            <label className="form-label">Amount (USD)</label>
            <input 
              type="number" 
              className="form-input" 
              step="0.01" 
              min="0.01" 
              placeholder="0.00" 
              value={formAmount}
              onChange={(e) => setFormAmount(e.target.value)}
              required 
            />
          </div>
        </form>
      </Modal>

      {/* CSV Import Modal */}
      <Modal
        isOpen={isImportModalOpen}
        title="📥 Bulk Transaction CSV Import"
        onClose={() => setIsImportModalOpen(false)}
        footer={
          <button className="btn btn-secondary" onClick={() => setIsImportModalOpen(false)}>Close Window</button>
        }
      >
        {importError && <div className="alert alert-danger">{importError}</div>}
        
        <form onSubmit={handleCsvUpload} style={{ display: 'flex', flexDirection: 'column', gap: '15px' }}>
          <div className="form-group">
            <label className="form-label">Select Transaction Catalog CSV File</label>
            <input 
              type="file" 
              accept=".csv"
              className="form-input"
              onChange={(e) => setUploadFile(e.target.files[0])}
              required
            />
            <span style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', marginTop: '4px' }}>
              Required CSV fields (auto-detects synonyms): <code>transaction_code</code>, <code>transaction_date</code>, <code>region</code>, <code>category</code>, <code>amount</code>
            </span>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '15px' }}>
            <div className="form-group">
              <label className="form-label">Duplicate Code Resolution Policy</label>
              <select 
                className="form-select" 
                value={duplicateAction} 
                onChange={(e) => setDuplicateAction(e.target.value)}
              >
                <option value="SKIP">Skip Duplicates (Default)</option>
                <option value="UPDATE">Update Existing Transaction Rows</option>
                <option value="REPLACE">Replace Existing (Delete & Re-insert)</option>
                <option value="INSERT_AS_NEW">Insert As New (Generate Code)</option>
                <option value="REJECT">Reject Row if Duplicate Found</option>
              </select>
            </div>

            <div className="form-group">
              <label className="form-label">Missing Region Policy</label>
              <select 
                className="form-select" 
                value={missingRegionPolicy} 
                onChange={(e) => setMissingRegionPolicy(e.target.value)}
              >
                <option value="ASSIGN_UNKNOWN">Assign "Unknown"</option>
                <option value="SKIP_ROW">Skip Ingestion Row</option>
              </select>
            </div>
          </div>

          <div className="form-group">
            <label className="form-label">Missing/New Category Policy</label>
            <select 
              className="form-select" 
              value={missingCategoryPolicy} 
              onChange={(e) => setMissingCategoryPolicy(e.target.value)}
            >
              <option value="CREATE_AUTOMATIC">Create Automatically</option>
              <option value="SKIP_ROW">Skip Ingestion Row</option>
            </select>
          </div>

          <button 
            type="submit" 
            className="btn btn-primary" 
            style={{ alignSelf: 'flex-start' }}
            disabled={importing}
          >
            {importing ? 'Processing Import...' : 'Upload & Process'}
          </button>
        </form>

        {importing && (
          <div style={{ marginTop: '20px', padding: '15px', background: 'var(--bg-color)', border: '1px solid var(--border-color)', borderRadius: '6px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '12px' }}>
              <div className="spinner" style={{ width: '16px', height: '16px', border: '2px solid var(--border-color)', borderTop: '2px solid var(--primary-color)' }}></div>
              <strong style={{ fontSize: '0.85rem' }}>Ingesting Data Quality Pipeline...</strong>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', fontSize: '0.8rem' }}>
              <div>{etlStep >= 1 ? '✅' : '⚪'} Reading File...</div>
              <div>{etlStep >= 2 ? '✅' : '⚪'} Mapping Columns...</div>
              <div>{etlStep >= 3 ? '✅' : '⚪'} Validating Data...</div>
              <div>{etlStep >= 4 ? '✅' : '⚪'} Checking Duplicates...</div>
              <div>{etlStep >= 5 ? '✅' : '⚪'} Saving Transactions...</div>
            </div>
          </div>
        )}

        {importResult && (
          <div style={{ marginTop: '20px', borderTop: '1px solid var(--border-color)', paddingTop: '15px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '15px' }}>
              <h4 style={{ fontSize: '0.95rem', margin: '0' }}>Import Processing Summary</h4>
              <span style={{
                backgroundColor: importResult.status === 'SUCCESS' ? '#10b981' : (importResult.status === 'PARTIAL_SUCCESS' ? '#f59e0b' : '#ef4444'),
                color: '#fff',
                padding: '4px 8px',
                borderRadius: '4px',
                fontSize: '0.75rem',
                fontWeight: 'bold'
              }}>
                {importResult.status}
              </span>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '15px', marginBottom: '15px' }}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', fontSize: '0.85rem' }}>
                <div>📁 Total Parsed Rows: <strong>{importResult.totalRecords}</strong></div>
                <div>✅ Successfully Imported: <strong>{importResult.importedRecords}</strong></div>
                <div>⚠️ Duplicate Codes Handled: <strong>{importResult.duplicateRecords}</strong></div>
                <div>❌ Failed (Validation Errors): <strong>{importResult.failedRecords}</strong></div>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', fontSize: '0.85rem' }}>
                <div>⚡ Processing Time: <strong>{importResult.processingTimeMs} ms</strong></div>
                <div>📈 Avg. Speed: <strong>{importResult.averageSpeedRecordsPerSec} records/s</strong></div>
                <div>🚫 Ignored Columns: <strong>{importResult.ignoredColumnsCount}</strong></div>
                {importResult.ignoredColumnsCount > 0 && (
                  <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', wordBreak: 'break-all' }}>
                    ({importResult.ignoredColumns.join(', ')})
                  </div>
                )}
              </div>
            </div>

            <div style={{ display: 'flex', gap: '10px', marginTop: '15px', marginBottom: '15px' }}>
              <button 
                type="button" 
                className="btn btn-secondary" 
                style={{ flex: 1, fontSize: '0.8rem', padding: '8px' }}
                onClick={downloadProcessingReport}
              >
                📊 Download Processing Report
              </button>
              {importResult.errors.length > 0 && (
                <button 
                  type="button" 
                  className="btn btn-danger" 
                  style={{ flex: 1, fontSize: '0.8rem', padding: '8px' }}
                  onClick={downloadErrorLog}
                >
                  ⚠️ Download Error Log
                </button>
              )}
            </div>

            {importResult.errors.length > 0 && (
              <div style={{ marginTop: '15px' }}>
                <h5 style={{ fontSize: '0.85rem', color: 'var(--error-color)', marginBottom: '8px', marginTop: '0' }}>
                  Parsing Violations & Errors ({importResult.errors.length})
                </h5>
                <div style={{ maxHeight: '150px', overflowY: 'auto', border: '1px solid var(--border-color)', borderRadius: '4px', padding: '8px', background: 'var(--bg-color)', fontSize: '0.75rem' }}>
                  {importResult.errors.map((err, index) => (
                    <div key={index} style={{ marginBottom: '6px', borderBottom: '1px solid var(--border-color)', paddingBottom: '4px' }}>
                      <strong>Line {err.lineNumber}:</strong> {err.errorMessage} <br/>
                      <span style={{ color: 'var(--text-secondary)', fontStyle: 'italic' }}>Raw: {err.rawRow}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}
      </Modal>
    </div>
  );
}
