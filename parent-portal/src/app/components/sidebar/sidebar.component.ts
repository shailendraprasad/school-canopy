import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../services/auth.service';

interface NavItem {
  path: string;
  label: string;
  icon: string;
}

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  template: `
    <aside class="sidebar">
      <div class="sidebar-brand">
        <span class="brand-icon">🌳</span>
        <span class="brand-name">Parent Portal</span>
      </div>
      <nav class="sidebar-nav">
        @for (item of navItems; track item.path) {
          <a [routerLink]="item.path" routerLinkActive="active" [routerLinkActiveOptions]="{ paths: 'subset', queryParams: 'ignored', matrixParams: 'ignored', fragment: 'ignored' }" class="nav-item">
            {{ item.icon }} {{ item.label }}
          </a>
        }
      </nav>
      <div class="sidebar-footer">
        <div class="user-info">
          <span class="user-name">{{ auth.currentUser()?.name }}</span>
          <span class="user-role">Parent</span>
        </div>
        <button (click)="auth.logout()" class="logout-btn">Logout</button>
      </div>
    </aside>
  `,
  styles: [`
    .sidebar {
      position: fixed;
      top: 0;
      left: 0;
      width: 240px;
      height: 100vh;
      background: #1a1a2e;
      color: #fff;
      display: flex;
      flex-direction: column;
      z-index: 100;
    }
    .sidebar-brand {
      padding: 20px 16px;
      display: flex;
      align-items: center;
      gap: 10px;
      border-bottom: 1px solid rgba(255,255,255,0.1);
    }
    .brand-icon { font-size: 24px; }
    .brand-name { font-family: 'Poppins', sans-serif; font-weight: 600; font-size: 16px; }
    .sidebar-nav {
      flex: 1;
      padding: 16px 0;
      display: flex;
      flex-direction: column;
      gap: 2px;
    }
    .nav-item {
      display: block;
      padding: 10px 20px;
      color: rgba(255,255,255,0.7);
      text-decoration: none;
      font-size: 14px;
      transition: all 0.2s;
      border-left: 3px solid transparent;
    }
    .nav-item:hover { background: rgba(255,255,255,0.05); color: #fff; }
    .nav-item.active {
      background: rgba(74, 107, 138, 0.2);
      color: #fff;
      border-left-color: #4a6b8a;
    }
    .sidebar-footer {
      padding: 16px;
      border-top: 1px solid rgba(255,255,255,0.1);
    }
    .user-info { margin-bottom: 8px; }
    .user-name { display: block; font-size: 13px; font-weight: 500; }
    .user-role { display: block; font-size: 11px; color: rgba(255,255,255,0.5); }
    .logout-btn {
      width: 100%;
      padding: 8px;
      background: rgba(255,255,255,0.1);
      color: #fff;
      border: none;
      border-radius: 6px;
      cursor: pointer;
      font-size: 13px;
      transition: background 0.2s;
    }
    .logout-btn:hover { background: rgba(255,255,255,0.2); }
  `]
})
export class SidebarComponent {
  navItems: NavItem[] = [
    { path: '/home', label: 'Home', icon: '🏠' },
    { path: '/news', label: 'News & Events', icon: '📣' },
    { path: '/messages', label: 'Messages', icon: '💬' },
  ];

  constructor(public auth: AuthService) {}
}
