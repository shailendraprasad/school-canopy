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
      { path: 'students', loadComponent: () => import('./pages/students/students.component').then(m => m.StudentsComponent) },
      { path: 'students/new', loadComponent: () => import('./pages/student-create/student-create.component').then(m => m.StudentCreateComponent) },
      { path: 'students/:id', loadComponent: () => import('./pages/student-detail/student-detail.component').then(m => m.StudentDetailComponent) },
      { path: 'classes', loadComponent: () => import('./pages/classes/classes.component').then(m => m.ClassesComponent) },
      { path: 'classes/:id', loadComponent: () => import('./pages/class-detail/class-detail.component').then(m => m.ClassDetailComponent) },
      { path: 'teachers', loadComponent: () => import('./pages/teachers/teachers.component').then(m => m.TeachersComponent) },
      { path: 'teachers/new', loadComponent: () => import('./pages/teacher-create/teacher-create.component').then(m => m.TeacherCreateComponent) },
      { path: 'teachers/:id', loadComponent: () => import('./pages/teacher-detail/teacher-detail.component').then(m => m.TeacherDetailComponent) },
      { path: 'staff', loadComponent: () => import('./pages/staff/staff.component').then(m => m.StaffComponent) },
      { path: 'staff/new', loadComponent: () => import('./pages/staff-create/staff-create.component').then(m => m.StaffCreateComponent) },
      { path: 'staff/:id', loadComponent: () => import('./pages/staff-detail/staff-detail.component').then(m => m.StaffDetailComponent) },
      { path: 'announcements', loadComponent: () => import('./pages/announcements/announcements.component').then(m => m.AnnouncementsComponent) },
      { path: 'events', loadComponent: () => import('./pages/events/events.component').then(m => m.EventsComponent) },
      { path: 'messages', loadComponent: () => import('./pages/messages/messages.component').then(m => m.MessagesComponent) },
      { path: 'attendance', loadComponent: () => import('./pages/attendance/attendance.component').then(m => m.AttendanceComponent) },
      { path: 'support', loadComponent: () => import('./pages/support/support.component').then(m => m.SupportComponent) },
      { path: 'settings', loadComponent: () => import('./pages/settings/settings.component').then(m => m.SettingsComponent) },
    ]
  },
  { path: '**', redirectTo: 'login' }
];
