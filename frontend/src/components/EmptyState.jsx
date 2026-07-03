import React from 'react';

export default function EmptyState({ title, description, actionText, onAction }) {
  return (
    <div className="empty-state">
      <div className="empty-state-icon">📊</div>
      <h3 className="empty-state-title">{title}</h3>
      <p className="empty-state-desc">{description}</p>
      {actionText && onAction && (
        <button className="btn btn-primary" onClick={onAction}>
          {actionText}
        </button>
      )}
    </div>
  );
}
