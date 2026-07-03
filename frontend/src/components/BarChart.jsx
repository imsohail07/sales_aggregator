import React from 'react';

export default function BarChart({ data, formatter }) {
  if (!data || data.length === 0) {
    return <div style={{ textAlign: 'center', padding: '20px', color: 'var(--text-secondary)' }}>No chart data.</div>;
  }

  // Find max value to normalize widths
  const maxVal = Math.max(...data.map(item => item.value), 0);

  return (
    <div className="bar-chart-container">
      {data.map((item, idx) => {
        const percentageWidth = maxVal > 0 ? (item.value * 100) / maxVal : 0;
        return (
          <div className="bar-row" key={idx}>
            <div className="bar-label" title={item.label}>
              {item.label}
            </div>
            <div className="bar-track">
              <div 
                className="bar-fill" 
                style={{ width: `${percentageWidth}%` }}
              ></div>
            </div>
            <div className="bar-value">
              {formatter ? formatter(item.value) : item.value}
            </div>
          </div>
        );
      })}
    </div>
  );
}
