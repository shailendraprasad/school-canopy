import { Component, signal } from '@angular/core';

import { CommonModule } from '@angular/common';

import { Router, RouterLink, RouterLinkActive } from '@angular/router';

import { AuthService } from '../../services/auth.service';



interface NavLink {

  path: string;

  label: string;

  icon: string;

  roles: string[];

}



interface NavGroup {

  id: string;

  label: string;

  icon: string;

  roles: string[];

  children: NavLink[];

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

        @for (item of topLinks; track item.path) {

          @if (isVisible(item.roles)) {

            <a [routerLink]="item.path" routerLinkActive="active" class="nav-item">{{ item.icon }} {{ item.label }}</a>

          }

        }



        @for (group of navGroups; track group.id) {

          @if (isGroupVisible(group)) {

            <div class="nav-group" [class.expanded]="isExpanded(group.id)">

              <button type="button" class="nav-group-header" [class.active]="isGroupActive(group)" (click)="toggleGroup(group.id)">

                <span class="nav-group-label">{{ group.icon }} {{ group.label }}</span>

                <span class="nav-chevron">{{ isExpanded(group.id) ? '▾' : '▸' }}</span>

              </button>

              @if (isExpanded(group.id)) {

                <div class="nav-group-children">

                  @for (child of group.children; track child.path) {

                    @if (isVisible(child.roles)) {

                      <a [routerLink]="child.path" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }" class="nav-item nav-child">{{ child.icon }} {{ child.label }}</a>

                    }

                  }

                </div>

              }

            </div>

          }

        }



        @for (item of bottomLinks; track item.path) {

          @if (isVisible(item.roles)) {

            <a [routerLink]="item.path" routerLinkActive="active" class="nav-item">{{ item.icon }} {{ item.label }}</a>

          }

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

  expandedGroups = signal<Record<string, boolean>>({

    communication: true,

    students: true,

    people: false,

    'school-setup': false,

  });



  topLinks: NavLink[] = [

    { path: '/dashboard', label: 'Dashboard', icon: '📊', roles: ['SCHOOL_ADMINISTRATOR', 'OFFICE_STAFF', 'TEACHER'] },

  ];



  navGroups: NavGroup[] = [

    {

      id: 'communication',

      label: 'Communication',

      icon: '📣',

      roles: ['SCHOOL_ADMINISTRATOR', 'TEACHER'],

      children: [

        { path: '/communication/announcements', label: 'Announcements', icon: '📢', roles: ['SCHOOL_ADMINISTRATOR', 'TEACHER'] },

        { path: '/communication/events', label: 'Events', icon: '📅', roles: ['SCHOOL_ADMINISTRATOR', 'TEACHER'] },

        { path: '/communication/messages', label: 'Messages', icon: '💬', roles: ['SCHOOL_ADMINISTRATOR', 'TEACHER'] },

      ],

    },

    {

      id: 'students',

      label: 'Students',

      icon: '🎒',

      roles: ['SCHOOL_ADMINISTRATOR', 'OFFICE_STAFF', 'TEACHER'],

      children: [

        { path: '/students', label: 'All Students', icon: '📋', roles: ['SCHOOL_ADMINISTRATOR', 'OFFICE_STAFF', 'TEACHER'] },

        { path: '/students/admissions', label: 'Admissions', icon: '📝', roles: ['SCHOOL_ADMINISTRATOR', 'OFFICE_STAFF'] },

        { path: '/students/attendance', label: 'Attendance', icon: '✅', roles: ['SCHOOL_ADMINISTRATOR', 'TEACHER'] },

        { path: '/students/reports', label: 'Reports', icon: '📊', roles: ['SCHOOL_ADMINISTRATOR', 'OFFICE_STAFF'] },
        { path: '/school-setup/classes', label: 'Classes & Sections', icon: '📚', roles: ['OFFICE_STAFF'] },

      ],

    },

    {

      id: 'people',

      label: 'People',

      icon: '👥',

      roles: ['SCHOOL_ADMINISTRATOR'],

      children: [

        { path: '/people/teachers', label: 'Teachers', icon: '👩‍🏫', roles: ['SCHOOL_ADMINISTRATOR'] },

        { path: '/people/staff', label: 'Office Staff', icon: '🏢', roles: ['SCHOOL_ADMINISTRATOR'] },

      ],

    },

    {

      id: 'school-setup',

      label: 'School Setup',

      icon: '🏫',

      roles: ['SCHOOL_ADMINISTRATOR'],

      children: [

        { path: '/school-setup/classes', label: 'Classes & Sections', icon: '📚', roles: ['SCHOOL_ADMINISTRATOR'] },

        { path: '/school-setup/academic-years', label: 'Academic Years', icon: '📅', roles: ['SCHOOL_ADMINISTRATOR'] },

        { path: '/school-setup/subjects', label: 'Subjects', icon: '📖', roles: ['SCHOOL_ADMINISTRATOR'] },

        { path: '/school-setup/promote', label: 'Promote Students', icon: '🎓', roles: ['SCHOOL_ADMINISTRATOR'] },

      ],

    },

  ];



  bottomLinks: NavLink[] = [

    { path: '/support', label: 'Support', icon: '🎫', roles: ['SCHOOL_ADMINISTRATOR', 'OFFICE_STAFF'] },

    { path: '/settings', label: 'Settings', icon: '⚙️', roles: ['SCHOOL_ADMINISTRATOR'] },

  ];



  constructor(public auth: AuthService, private router: Router) {

    this.expandActiveGroup();

  }



  isVisible(roles: string[]): boolean {

    const role = this.auth.currentUser()?.role || '';

    return roles.includes(role);

  }



  isGroupVisible(group: NavGroup): boolean {

    return this.isVisible(group.roles) && group.children.some(c => this.isVisible(c.roles));

  }



  isExpanded(groupId: string): boolean {

    return this.expandedGroups()[groupId] ?? false;

  }



  toggleGroup(groupId: string) {

    const current = this.expandedGroups();

    this.expandedGroups.set({ ...current, [groupId]: !current[groupId] });

  }



  isGroupActive(group: NavGroup): boolean {

    const url = this.router.url;

    return group.children.some(c => url === c.path || url.startsWith(c.path + '/'));

  }



  private expandActiveGroup() {

    const url = this.router.url;

    const expanded = { ...this.expandedGroups() };

    for (const group of this.navGroups) {

      if (group.children.some(c => url === c.path || url.startsWith(c.path + '/') || url.startsWith(c.path.replace(/\/$/, '')))) {

        expanded[group.id] = true;

      }

      // Also expand for legacy/detail paths

      if (group.id === 'students' && (url.startsWith('/students/') && !url.includes('/admissions') && !url.includes('/attendance') && !url.includes('/reports'))) {

        expanded['students'] = true;

      }

      if (group.id === 'people' && (url.startsWith('/teachers') || url.startsWith('/people'))) {

        expanded['people'] = true;

      }

      if (group.id === 'school-setup' && (url.startsWith('/school-setup') || url.startsWith('/classes/'))) {

        expanded['school-setup'] = true;

      }

      if (group.id === 'communication' && url.startsWith('/communication')) {

        expanded['communication'] = true;

      }

    }

    this.expandedGroups.set(expanded);

  }



  formatRole(role: string | undefined): string {

    if (!role) return '';

    return role.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase());

  }

}
