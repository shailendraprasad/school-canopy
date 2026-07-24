import { Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { ApiService } from './api.service';

@Injectable({ providedIn: 'root' })
export class AuthService {
  isAuthenticated = signal(false);
  currentUser = signal<any>(null);

  constructor(private api: ApiService, private router: Router) {
    const saved = localStorage.getItem('user');
    if (saved) {
      this.currentUser.set(JSON.parse(saved));
      this.isAuthenticated.set(true);
    }
  }

  login(email: string, password: string) {
    return this.api.login(email, password).pipe().subscribe({
      next: (res) => {
        this.currentUser.set(res.data);
        this.isAuthenticated.set(true);
        localStorage.setItem('user', JSON.stringify(res.data));
        this.router.navigate(['/dashboard']);
      },
      error: (err) => {
        throw err;
      }
    });
  }

  logout() {
    this.api.logout().subscribe(() => {
      this.currentUser.set(null);
      this.isAuthenticated.set(false);
      localStorage.removeItem('user');
      this.router.navigate(['/login']);
    });
  }
}
