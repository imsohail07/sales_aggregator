import React, { useContext } from 'react';
import { AuthContext } from '../context/AuthContext';

export default function Header({ title }) {
  const { theme, toggleTheme, user } = useContext(AuthContext);

  const getRoleName = (roleString) => {
    if (!roleString) return 'Viewer';
    return roleString
      .replace('ROLE_', '')
      .split('_')
      .map(w => w.charAt(0) + w.slice(1).toLowerCase())
      .join(' ');
  };

  return (
    <header className="main-header">
      <h2 className="header-title">{title}</h2>
      <div className="header-user-actions">
        <button className="theme-toggle-btn" onClick={toggleTheme}>
          {theme === 'light' ? '🌙 Dark Mode' : '☀️ Light Mode'}
        </button>
        <span className="user-badge">
          👤 {user?.username} ({getRoleName(user?.role)})
        </span>
      </div>
    </header>
  );
}
