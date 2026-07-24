import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';

@Component({ selector: 'app-config', standalone: true, imports: [CommonModule, FormsModule, RouterLink], templateUrl: './config.component.html', styleUrl: './config.component.css' })
export class ConfigComponent implements OnInit {
  config = signal<any>({});
  saving = signal(false);
  message = signal('');

  constructor(private api: ApiService, private auth: AuthService) {}

  ngOnInit() { this.load(); }

  load() {
    this.api.get<any>('/api/platform/config').subscribe(res => this.config.set(res.data || {}));
  }

  save() {
    this.saving.set(true);
    this.message.set('');
    this.api.put('/api/platform/config', this.config()).subscribe({
      next: (res) => { this.config.set(res.data || {}); this.saving.set(false); this.message.set('Configuration saved successfully.'); },
      error: (err) => { this.saving.set(false); this.message.set(err.error?.errors?.[0]?.message || 'Save failed'); }
    });
  }

  update(key: string, value: string) {
    const c = { ...this.config() };
    c[key] = value;
    this.config.set(c);
  }
}
