import { Routes } from '@angular/router';
import { LayoutComponent } from './components/layout/layout.component';

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'login', loadComponent: () => import('./pages/login/login.component').then(m => m.LoginComponent) },
  { path: 'setup/:token', loadComponent: () => import('./pages/setup/setup.component').then(m => m.SetupComponent) },
  {
    path: '',
    component: LayoutComponent,
    children: [
      { path: 'dashboard', loadComponent: () => import('./pages/dashboard/dashboard.component').then(m => m.DashboardComponent) },
      { path: 'schools', loadComponent: () => import('./pages/schools/schools.component').then(m => m.SchoolsComponent) },
      { path: 'team-members', loadComponent: () => import('./pages/team-members/team-members.component').then(m => m.TeamMembersComponent) },
      { path: 'school-admins', loadComponent: () => import('./pages/school-admins/school-admins.component').then(m => m.SchoolAdminsComponent) },
      { path: 'config', loadComponent: () => import('./pages/config/config.component').then(m => m.ConfigComponent) },
      { path: 'audit', loadComponent: () => import('./pages/audit/audit.component').then(m => m.AuditComponent) },
      { path: 'support', loadComponent: () => import('./pages/support/support.component').then(m => m.SupportComponent) },
    ]
  },
  { path: '**', redirectTo: 'login' }
];
