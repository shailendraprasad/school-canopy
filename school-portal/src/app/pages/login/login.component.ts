import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css'
})
export class LoginComponent implements OnInit {
  email = '';
  password = '';
  error = signal('');
  loading = signal(false);
  sessionExpired = signal(false);

  constructor(private api: ApiService, private auth: AuthService, private router: Router, private route: ActivatedRoute) {}

  ngOnInit() {
    if (this.route.snapshot.queryParams['expired'] === 'true') {
      this.sessionExpired.set(true);
    }
  }

  onSubmit() {
    this.error.set('');
    this.sessionExpired.set(false);
    this.loading.set(true);
    this.api.login(this.email, this.password).subscribe({
      next: (res) => {
        localStorage.setItem('school_token', res.data.token);
        this.auth.currentUser.set(res.data);
        this.auth.isAuthenticated.set(true);
        localStorage.setItem('school_user', JSON.stringify(res.data));
        this.auth.applyBranding();
        this.loading.set(false);
        this.router.navigate(['/dashboard']);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.error?.errors?.[0]?.message || 'Invalid credentials');
      }
    });
  }
}
