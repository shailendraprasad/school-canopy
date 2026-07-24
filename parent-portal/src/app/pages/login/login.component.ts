import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css'
})
export class LoginComponent {
  email = '';
  password = '';
  loading = signal(false);
  error = signal('');

  constructor(private api: ApiService, private auth: AuthService, private router: Router) {}

  login() {
    this.loading.set(true);
    this.error.set('');
    this.api.login(this.email, this.password).subscribe({
      next: (res) => {
        localStorage.setItem('parent_token', res.data.token);
        const user = { name: res.data.name, email: res.data.email, role: res.data.role };
        this.auth.currentUser.set(user);
        this.auth.isAuthenticated.set(true);
        localStorage.setItem('parent_user', JSON.stringify(user));
        this.router.navigate(['/home']);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err.error?.errors?.[0]?.message || 'Invalid credentials');
        this.loading.set(false);
      }
    });
  }
}
