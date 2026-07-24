import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="settings-page">
      <h2>⚙️ School Settings</h2>
      <p class="subtitle">Customize your school's portal appearance</p>

      <div class="settings-card">
        <h3>🎨 Branding</h3>
        <p class="desc">Choose a brand color and logo to personalize the portal for your school.</p>

        @if (saveSuccess()) { <div class="success-msg">✓ Branding updated! Changes will apply on next login.</div> }
        @if (saveError()) { <div class="error-msg">{{ saveError() }}</div> }

        <div class="form-grid">
          <div class="form-group">
            <label>Brand Color</label>
            <div class="color-picker-row">
              <input type="color" [(ngModel)]="brandColor" class="color-input" />
              <input type="text" [(ngModel)]="brandColor" class="color-text" placeholder="#4a6b8a" maxlength="7" />
            </div>
            <span class="hint">This color is used for sidebar active state, buttons, and accents.</span>
          </div>
          <div class="form-group">
            <label>Logo URL</label>
            <input type="text" [(ngModel)]="logoUrl" placeholder="https://your-school.com/logo.png" class="text-input" />
            <span class="hint">Paste a URL to your school's logo image (square, min 64x64px).</span>
          </div>
        </div>

        @if (logoUrl) {
          <div class="logo-preview">
            <img [src]="logoUrl" alt="Logo preview" class="preview-img" />
            <span>Logo Preview</span>
          </div>
        }

        <div class="color-preview">
          <span>Preview:</span>
          <div class="preview-bar" [style.background]="brandColor"></div>
          <div class="preview-btn" [style.background]="brandColor">Button</div>
        </div>

        <button (click)="save()" class="btn-save" [style.background]="brandColor">Save Branding</button>
      </div>
    </div>
  `,
  styles: [`
    .settings-page { max-width: 700px; }
    h2 { font-size: 1.4rem; color: #1a1a2e; margin: 0; }
    .subtitle { font-size: .85rem; color: #888; margin: 4px 0 20px; }
    .settings-card { background: #fff; border-radius: 14px; padding: 24px; box-shadow: 0 1px 4px rgba(0,0,0,.04); }
    .settings-card h3 { font-size: 1rem; margin: 0 0 4px; }
    .desc { font-size: .85rem; color: #666; margin: 0 0 20px; }
    .form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-bottom: 20px; }
    .form-group { display: flex; flex-direction: column; gap: 6px; }
    .form-group label { font-size: .8rem; font-weight: 600; color: #333; }
    .hint { font-size: .72rem; color: #999; }
    .color-picker-row { display: flex; gap: 8px; align-items: center; }
    .color-input { width: 44px; height: 44px; border: none; border-radius: 8px; cursor: pointer; padding: 0; }
    .color-text { flex: 1; padding: 10px 12px; border: 1px solid #ddd; border-radius: 8px; font-size: .9rem; font-family: monospace; outline: 0; }
    .color-text:focus { border-color: #4a6b8a; }
    .text-input { padding: 10px 12px; border: 1px solid #ddd; border-radius: 8px; font-size: .9rem; outline: 0; width: 100%; }
    .text-input:focus { border-color: #4a6b8a; }
    .logo-preview { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; padding: 12px; background: #f8f9fb; border-radius: 8px; }
    .preview-img { width: 48px; height: 48px; border-radius: 8px; object-fit: cover; border: 1px solid #eee; }
    .logo-preview span { font-size: .82rem; color: #888; }
    .color-preview { display: flex; align-items: center; gap: 12px; margin-bottom: 20px; }
    .color-preview span { font-size: .82rem; color: #888; }
    .preview-bar { width: 120px; height: 8px; border-radius: 4px; }
    .preview-btn { padding: 6px 14px; color: #fff; border-radius: 6px; font-size: .8rem; font-weight: 600; }
    .btn-save { padding: 12px 24px; color: #fff; border: none; border-radius: 8px; cursor: pointer; font-weight: 600; font-size: .9rem; }
    .btn-save:hover { opacity: .9; }
    .success-msg { background: #e7f3ea; color: #2d6a4f; padding: 10px 14px; border-radius: 8px; font-size: .85rem; margin-bottom: 16px; }
    .error-msg { background: #fbeaea; color: #b0473f; padding: 10px 14px; border-radius: 8px; font-size: .85rem; margin-bottom: 16px; }
    @media (max-width: 600px) { .form-grid { grid-template-columns: 1fr; } }
  `]
})
export class SettingsComponent implements OnInit {
  brandColor = '#4a6b8a';
  logoUrl = '';
  saveSuccess = signal(false);
  saveError = signal('');

  constructor(private api: ApiService, private auth: AuthService) {}

  ngOnInit() {
    this.api.get<any>('/api/school/branding').subscribe(res => {
      if (res.data) {
        this.brandColor = res.data.brandColor || '#4a6b8a';
        this.logoUrl = res.data.logoUrl || '';
      }
    });
  }

  save() {
    this.saveSuccess.set(false);
    this.saveError.set('');
    this.api.put('/api/school/branding', { brandColor: this.brandColor, logoUrl: this.logoUrl }).subscribe({
      next: (res: any) => {
        this.saveSuccess.set(true);
        // Update local storage with new branding
        const user = this.auth.currentUser();
        if (user) {
          user.brandColor = this.brandColor;
          user.logoUrl = this.logoUrl;
          this.auth.currentUser.set({ ...user });
          localStorage.setItem('school_user', JSON.stringify(user));
          this.auth.applyBranding();
        }
      },
      error: (err) => this.saveError.set(err.error?.errors?.[0]?.message || 'Failed to save')
    });
  }
}
