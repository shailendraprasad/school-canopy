import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';

@Component({
  selector: 'app-setup',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="setup-page">
      <div class="setup-card">
        <div class="setup-header">
          <span class="logo">🌳</span>
          <h1>School Canopy</h1>
          <p>Parent Account Setup</p>
        </div>

        @if (step() === 'error') {
          <div class="error-card">
            <div class="error-icon">⚠️</div>
            <h3>{{ errorTitle() }}</h3>
            <p>{{ errorMessage() }}</p>
            <button (click)="goToLogin()" class="btn-primary">Go to Login</button>
          </div>
        }

        @if (step() === 'form') {
          <div class="setup-info">
            <p>Your child's school has invited you to School Canopy. Set up your password to get started.</p>
          </div>
          @if (formError()) { <div class="form-error">{{ formError() }}</div> }
          <form (ngSubmit)="onSubmit()" class="setup-form">
            <div class="field">
              <label>New Password</label>
              <input type="password" [(ngModel)]="password" name="password" placeholder="Enter a strong password" required minlength="8" />
              <span class="field-hint">Minimum 8 characters</span>
            </div>
            <div class="field">
              <label>Confirm Password</label>
              <input type="password" [(ngModel)]="confirmPassword" name="confirmPassword" placeholder="Re-enter your password" required />
            </div>
            <button type="submit" [disabled]="submitting()" class="btn-primary">
              {{ submitting() ? 'Setting up...' : 'Activate Account' }}
            </button>
          </form>
        }

        @if (step() === 'success') {
          <div class="success-card">
            <div class="success-icon">✓</div>
            <h3>Account Activated!</h3>
            <p>You can now sign in to view your child's updates, messages, and more.</p>
            <button (click)="goToLogin()" class="btn-primary">Sign In →</button>
          </div>
        }
      </div>
    </div>
  `,
  styles: [`
    .setup-page { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%); padding: 20px; }
    .setup-card { background: #fff; border-radius: 16px; padding: 40px; width: 100%; max-width: 440px; box-shadow: 0 20px 60px rgba(0,0,0,0.3); }
    .setup-header { text-align: center; margin-bottom: 28px; }
    .logo { font-size: 48px; display: block; margin-bottom: 12px; }
    .setup-header h1 { font-size: 1.4rem; color: #1a1a2e; margin: 0 0 4px; }
    .setup-header p { color: #888; font-size: .9rem; margin: 0; }
    .setup-info { margin-bottom: 20px; text-align: center; }
    .setup-info p { color: #555; font-size: .9rem; margin: 0; }
    .error-card, .success-card { text-align: center; padding: 10px 0; }
    .error-icon { font-size: 2.5rem; margin-bottom: 12px; }
    .success-icon { width: 56px; height: 56px; background: #e7f3ea; color: #2d6a4f; border-radius: 50%; display: inline-flex; align-items: center; justify-content: center; font-size: 1.5rem; font-weight: 700; }
    .error-card h3, .success-card h3 { font-size: 1.1rem; margin: 0 0 8px; color: #1a1a2e; }
    .error-card p, .success-card p { color: #666; font-size: .9rem; margin: 0 0 20px; }
    .setup-form { display: flex; flex-direction: column; gap: 18px; }
    .field { display: flex; flex-direction: column; gap: 6px; }
    .field label { font-size: .85rem; font-weight: 500; color: #333; }
    .field input { padding: 12px 14px; border: 1.5px solid #e0e0e0; border-radius: 10px; font-size: .9rem; outline: none; transition: border-color .2s; }
    .field input:focus { border-color: #4a6b8a; box-shadow: 0 0 0 3px rgba(74,107,138,.1); }
    .field-hint { font-size: .75rem; color: #999; }
    .form-error { background: #fbeaea; color: #b0473f; padding: 10px 14px; border-radius: 8px; font-size: .85rem; margin-bottom: 4px; }
    .btn-primary { padding: 14px; background: #4a6b8a; color: #fff; border: none; border-radius: 10px; font-size: .95rem; font-weight: 600; cursor: pointer; width: 100%; margin-top: 4px; }
    .btn-primary:hover:not(:disabled) { background: #33506b; }
    .btn-primary:disabled { opacity: .6; cursor: not-allowed; }
  `]
})
export class SetupComponent implements OnInit {
  token = '';
  password = '';
  confirmPassword = '';
  step = signal<'form' | 'error' | 'success'>('form');
  formError = signal('');
  errorTitle = signal('');
  errorMessage = signal('');
  submitting = signal(false);
  private baseUrl = 'http://localhost:8080';

  constructor(private route: ActivatedRoute, private router: Router, private http: HttpClient) {}

  ngOnInit() {
    this.token = this.route.snapshot.paramMap.get('token') || '';
    if (!this.token) { this.errorTitle.set('Invalid Link'); this.errorMessage.set('This invitation link is invalid.'); this.step.set('error'); }
  }

  onSubmit() {
    this.formError.set('');
    if (!this.password || this.password.length < 8) { this.formError.set('Password must be at least 8 characters'); return; }
    if (this.password !== this.confirmPassword) { this.formError.set('Passwords do not match'); return; }
    this.submitting.set(true);
    this.http.post<any>(`${this.baseUrl}/api/invitations/${this.token}/setup`, { password: this.password }).subscribe({
      next: () => { this.submitting.set(false); this.step.set('success'); },
      error: (err) => {
        this.submitting.set(false);
        const msg = err.error?.errors?.[0]?.message || 'Failed';
        const code = err.error?.errors?.[0]?.code || '';
        if (['EXPIRED', 'USED', 'INVALID'].includes(code)) { this.errorTitle.set(code === 'EXPIRED' ? 'Expired' : code === 'USED' ? 'Already Used' : 'Invalid'); this.errorMessage.set(msg); this.step.set('error'); }
        else { this.formError.set(msg); }
      }
    });
  }

  goToLogin() { this.router.navigate(['/login']); }
}
