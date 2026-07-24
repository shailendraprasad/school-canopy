import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../services/auth.service';
import { ApiService } from '../../services/api.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css'
})
export class LoginComponent {
  email = '';
  password = '';
  error = signal('');
  loading = signal(false);

  constructor(private api: ApiService, private auth: AuthService, private router: Router) {}

  onSubmit() {
    this.error.set('');
    this.loading.set(true);

    this.api.login(this.email, this.password).subscribe({
      next: (res) => {
        localStorage.setItem('platform_token', res.data.token);
        this.auth.currentUser.set(res.data);
        this.auth.isAuthenticated.set(true);
        localStorage.setItem('user', JSON.stringify(res.data));
        this.loading.set(false);
        this.router.navigate(['/dashboard']);
      },
      error: (err) => {
        this.loading.set(false);
        if (err.error?.errors?.length) {
          this.error.set(err.error.errors[0].message);
        } else {
          this.error.set('Invalid credentials');
        }
      }
    });
  }
}
