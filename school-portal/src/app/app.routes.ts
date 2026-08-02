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

      // Students hub (list + admissions + attendance + reports)
      { path: 'students/new', loadComponent: () => import('./pages/student-create/student-create.component').then(m => m.StudentCreateComponent) },
      {
        path: 'students',
        loadComponent: () => import('./components/hubs/students-hub.component').then(m => m.StudentsHubComponent),
        children: [
          { path: '', loadComponent: () => import('./pages/students/students.component').then(m => m.StudentsComponent) },
          { path: 'admissions', loadComponent: () => import('./pages/admissions/admissions.component').then(m => m.AdmissionsComponent) },
          { path: 'attendance', loadComponent: () => import('./pages/attendance/attendance.component').then(m => m.AttendanceComponent) },
          { path: 'reports', loadComponent: () => import('./pages/reports/reports.component').then(m => m.ReportsComponent) },
        ]
      },
      { path: 'students/:id', loadComponent: () => import('./pages/student-detail/student-detail.component').then(m => m.StudentDetailComponent) },

      // Communication hub
      {
        path: 'communication',
        loadComponent: () => import('./components/hubs/communication-hub.component').then(m => m.CommunicationHubComponent),
        children: [
          { path: '', redirectTo: 'announcements', pathMatch: 'full' },
          { path: 'announcements', loadComponent: () => import('./pages/announcements/announcements.component').then(m => m.AnnouncementsComponent) },
          { path: 'events', loadComponent: () => import('./pages/events/events.component').then(m => m.EventsComponent) },
          { path: 'messages', loadComponent: () => import('./pages/messages/messages.component').then(m => m.MessagesComponent) },
        ]
      },

      // People hub (create/detail pages stay at top-level paths)
      { path: 'teachers/new', loadComponent: () => import('./pages/teacher-create/teacher-create.component').then(m => m.TeacherCreateComponent) },
      { path: 'teachers/:id', loadComponent: () => import('./pages/teacher-detail/teacher-detail.component').then(m => m.TeacherDetailComponent) },
      { path: 'staff/new', loadComponent: () => import('./pages/staff-create/staff-create.component').then(m => m.StaffCreateComponent) },
      { path: 'staff/:id', loadComponent: () => import('./pages/staff-detail/staff-detail.component').then(m => m.StaffDetailComponent) },
      {
        path: 'people',
        loadComponent: () => import('./components/hubs/people-hub.component').then(m => m.PeopleHubComponent),
        children: [
          { path: '', redirectTo: 'teachers', pathMatch: 'full' },
          { path: 'teachers', loadComponent: () => import('./pages/teachers/teachers.component').then(m => m.TeachersComponent) },
          { path: 'staff', loadComponent: () => import('./pages/staff/staff.component').then(m => m.StaffComponent) },
        ]
      },

      // School setup hub (class detail stays at top-level)
      { path: 'classes/:id', loadComponent: () => import('./pages/class-detail/class-detail.component').then(m => m.ClassDetailComponent) },
      {
        path: 'school-setup',
        loadComponent: () => import('./components/hubs/school-setup-hub.component').then(m => m.SchoolSetupHubComponent),
        children: [
          { path: '', redirectTo: 'classes', pathMatch: 'full' },
          { path: 'classes', loadComponent: () => import('./pages/classes/classes.component').then(m => m.ClassesComponent) },
          { path: 'academic-years', loadComponent: () => import('./pages/academic-years/academic-years.component').then(m => m.AcademicYearsComponent) },
          { path: 'subjects', loadComponent: () => import('./pages/subjects/subjects.component').then(m => m.SubjectsComponent) },
          { path: 'promote', loadComponent: () => import('./pages/promote-students/promote-students.component').then(m => m.PromoteStudentsComponent) },
        ]
      },

      { path: 'support', loadComponent: () => import('./pages/support/support.component').then(m => m.SupportComponent) },
      { path: 'settings', loadComponent: () => import('./pages/settings/settings.component').then(m => m.SettingsComponent) },

      // Legacy path redirects
      { path: 'announcements', redirectTo: 'communication/announcements', pathMatch: 'full' },
      { path: 'events', redirectTo: 'communication/events', pathMatch: 'full' },
      { path: 'messages', redirectTo: 'communication/messages', pathMatch: 'full' },
      { path: 'attendance', redirectTo: 'students/attendance', pathMatch: 'full' },
      { path: 'admissions', redirectTo: 'students/admissions', pathMatch: 'full' },
      { path: 'reports', redirectTo: 'students/reports', pathMatch: 'full' },
      { path: 'teachers', redirectTo: 'people/teachers', pathMatch: 'full' },
      { path: 'staff', redirectTo: 'people/staff', pathMatch: 'full' },
      { path: 'classes', redirectTo: 'school-setup/classes', pathMatch: 'full' },
      { path: 'academic-years', redirectTo: 'school-setup/academic-years', pathMatch: 'full' },
      { path: 'subjects', redirectTo: 'school-setup/subjects', pathMatch: 'full' },
      { path: 'promote-students', redirectTo: 'school-setup/promote', pathMatch: 'full' },
    ]
  },
  { path: '**', redirectTo: 'login' }
];
