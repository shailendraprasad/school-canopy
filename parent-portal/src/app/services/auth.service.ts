import { Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { ApiService } from './api.service';

@Injectable({ providedIn: 'root' })
export class AuthService {
  isAuthenticated = signal(false);
  currentUser = signal<any>(null);

  constructor(private api: ApiService, private router: Router) {
    const saved = localStorage.getItem('parent_user');
    if (saved) {
      this.currentUser.set(JSON.parse(saved));
      this.isAuthenticated.set(true);
    }
  }

  logout() {
    this.api.logout().subscribe(() => {
      this.currentUser.set(null);
      this.isAuthenticated.set(false);
      localStorage.removeItem('parent_user');
      this.router.navigate(['/login']);
    });
  }
}
