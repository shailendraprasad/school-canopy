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
      { path: 'home', loadComponent: () => import('./pages/home/home.component').then(m => m.HomeComponent) },
      { path: 'announcements', loadComponent: () => import('./pages/announcements/announcements.component').then(m => m.AnnouncementsComponent) },
      { path: 'events', loadComponent: () => import('./pages/events/events.component').then(m => m.EventsComponent) },
      { path: 'messages', loadComponent: () => import('./pages/messages/messages.component').then(m => m.MessagesComponent) },
    ]
  },
  { path: '**', redirectTo: 'login' }
];
