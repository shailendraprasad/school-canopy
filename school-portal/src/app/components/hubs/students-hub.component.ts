import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { HubTabsComponent, HubTab } from '../hub-tabs/hub-tabs.component';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-students-hub',
  standalone: true,
  imports: [RouterOutlet, HubTabsComponent],
  template: `
    <app-hub-tabs [tabs]="visibleTabs" />
    <router-outlet />
  `
})
export class StudentsHubComponent {
  constructor(private auth: AuthService) {}

  private allTabs: (HubTab & { roles: string[] })[] = [
    { path: '/students', label: 'All Students', icon: '🎒', roles: ['SCHOOL_ADMINISTRATOR', 'OFFICE_STAFF', 'TEACHER'] },
    { path: '/students/admissions', label: 'Admissions', icon: '📝', roles: ['SCHOOL_ADMINISTRATOR', 'OFFICE_STAFF'] },
    { path: '/students/attendance', label: 'Attendance', icon: '✅', roles: ['SCHOOL_ADMINISTRATOR', 'TEACHER'] },
    { path: '/students/reports', label: 'Reports', icon: '📊', roles: ['SCHOOL_ADMINISTRATOR', 'OFFICE_STAFF'] },
  ];

  get visibleTabs(): HubTab[] {
    const role = this.auth.currentUser()?.role || '';
    return this.allTabs.filter(t => t.roles.includes(role));
  }
}
