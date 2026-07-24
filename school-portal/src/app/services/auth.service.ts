import { Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { ApiService } from './api.service';

@Injectable({ providedIn: 'root' })
export class AuthService {
  isAuthenticated = signal(false);
  currentUser = signal<any>(null);

  constructor(private api: ApiService, private router: Router) {
    const saved = localStorage.getItem('school_user');
    if (saved) {
      this.currentUser.set(JSON.parse(saved));
      this.isAuthenticated.set(true);
      this.applyBranding();
    }
  }

  applyBranding() {
    const user = this.currentUser();
    if (user?.brandColor) {
      document.documentElement.style.setProperty('--brand-color', user.brandColor);
    }
    if (user?.logoUrl) {
      document.documentElement.style.setProperty('--logo-url', `url(${user.logoUrl})`);
    }
  }

  logout() {
    this.api.logout().subscribe(() => {
      this.currentUser.set(null);
      this.isAuthenticated.set(false);
      localStorage.removeItem('school_user');
      localStorage.removeItem('school_token');
      document.documentElement.style.removeProperty('--brand-color');
      document.documentElement.style.removeProperty('--logo-url');
      this.router.navigate(['/login']);
    });
  }
}
