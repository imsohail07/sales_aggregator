import React from 'react';

export default function KpiCard({ label, value, subtext }) {
  return (
    <div className="card kpi-card">
      <span className="kpi-label">{label}</span>
      <span className="kpi-value">{value}</span>
      {subtext && <span className="kpi-subtext">{subtext}</span>}
    </div>
  );
}
