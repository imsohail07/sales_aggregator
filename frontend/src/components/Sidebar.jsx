import React, { useContext } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { AuthContext } from '../context/AuthContext';

export default function Sidebar() {
  const { user, logout } = useContext(AuthContext);
  const location = useLocation();

  const getRoleName = (roleString) => {
    if (!roleString) return 'Viewer';
    // ROLE_ADMINISTRATOR -> Administrator
    return roleString
      .replace('ROLE_', '')
      .split('_')
      .map(w => w.charAt(0) + w.slice(1).toLowerCase())
      .join(' ');
  };

  const menuItems = [
    { path: '/dashboard', label: 'Dashboard', icon: '📊' },
    { path: '/transactions', label: 'Transactions', icon: '💸' },
    { path: '/analytics', label: 'Analytics', icon: '📈' },
    { path: '/reports', label: 'Reports', icon: '📋' }
  ];

  return (
    <aside className="sidebar">
      <div className="sidebar-brand">
        SalesSphere BI
      </div>
      <ul className="sidebar-menu">
        {menuItems.map((item) => {
          const isActive = location.pathname === item.path || (item.path === '/dashboard' && location.pathname === '/');
          return (
            <li className={`sidebar-item ${isActive ? 'active' : ''}`} key={item.path}>
              <Link to={item.path}>
                <span style={{ marginRight: '10px' }}>{item.icon}</span>
                {item.label}
              </Link>
            </li>
          );
        })}
      </ul>
      <div className="sidebar-footer">
        <div>
          <div style={{ fontWeight: '600', color: '#FFF' }}>{user?.username}</div>
          <div style={{ fontSize: '0.75rem', color: '#A0AEC0', marginTop: '2px' }}>
            {getRoleName(user?.role)}
          </div>
        </div>
        <button 
          className="btn btn-secondary btn-small" 
          onClick={logout}
          style={{ alignSelf: 'flex-start', color: '#FF7B7B', borderColor: '#FF7B7B', background: 'transparent' }}
        >
          🚪 Log Out
        </button>
      </div>
    </aside>
  );
}
