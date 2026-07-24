import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../services/auth.service';

interface NavItem {
  path: string;
  label: string;
  icon: string;
  roles: string[];
}

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  template: `
    <aside class="sidebar">
      <div class="sidebar-brand">
        @if (auth.currentUser()?.logoUrl) {
          <img [src]="auth.currentUser().logoUrl" class="brand-logo" alt="School logo" />
        } @else {
          <span class="brand-icon">🌳</span>
        }
        <span class="brand-name">{{ auth.currentUser()?.schoolName || 'School Canopy' }}</span>
      </div>
      <nav class="sidebar-nav">
        @for (item of visibleItems; track item.path) {
          <a [routerLink]="item.path" routerLinkActive="active" class="nav-item">{{ item.icon }} {{ item.label }}</a>
        }
      </nav>
      <div class="sidebar-footer">
        <div class="user-info">
          <span class="user-name">{{ auth.currentUser()?.name }}</span>
          <span class="user-role">{{ formatRole(auth.currentUser()?.role) }}</span>
        </div>
        <button (click)="auth.logout()" class="logout-btn">Logout</button>
      </div>
    </aside>
  `,
  styleUrl: './sidebar.component.css'
})
export class SidebarComponent {
  private allItems: NavItem[] = [
    { path: '/dashboard', label: 'Dashboard', icon: '📊', roles: ['SCHOOL_ADMINISTRATOR', 'OFFICE_STAFF', 'TEACHER'] },
    { path: '/students', label: 'Students', icon: '🎒', roles: ['SCHOOL_ADMINISTRATOR', 'OFFICE_STAFF', 'TEACHER'] },
    { path: '/attendance', label: 'Attendance', icon: '✅', roles: ['SCHOOL_ADMINISTRATOR', 'TEACHER'] },
    { path: '/classes', label: 'Classes & Sections', icon: '📚', roles: ['SCHOOL_ADMINISTRATOR', 'OFFICE_STAFF'] },
    { path: '/teachers', label: 'Teachers', icon: '👩‍🏫', roles: ['SCHOOL_ADMINISTRATOR'] },
    { path: '/staff', label: 'Office Staff', icon: '🏢', roles: ['SCHOOL_ADMINISTRATOR'] },
    { path: '/announcements', label: 'Announcements', icon: '📢', roles: ['SCHOOL_ADMINISTRATOR', 'TEACHER'] },
    { path: '/events', label: 'Events', icon: '📅', roles: ['SCHOOL_ADMINISTRATOR', 'TEACHER'] },
    { path: '/messages', label: 'Messages', icon: '💬', roles: ['SCHOOL_ADMINISTRATOR', 'TEACHER'] },
    { path: '/support', label: 'Support', icon: '🎫', roles: ['SCHOOL_ADMINISTRATOR', 'OFFICE_STAFF'] },
    { path: '/settings', label: 'Settings', icon: '⚙️', roles: ['SCHOOL_ADMINISTRATOR'] },
  ];

  constructor(public auth: AuthService) {}

  get visibleItems(): NavItem[] {
    const role = this.auth.currentUser()?.role || '';
    return this.allItems.filter(item => item.roles.includes(role));
  }

  formatRole(role: string | undefined): string {
    if (!role) return '';
    return role.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase());
  }
}
