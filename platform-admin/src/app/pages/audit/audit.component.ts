import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';

@Component({ selector: 'app-audit', standalone: true, imports: [CommonModule, FormsModule, RouterLink], templateUrl: './audit.component.html', styleUrl: './audit.component.css' })
export class AuditComponent implements OnInit {
  logs = signal<any[]>([]);
  actionType = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  ngOnInit() { this.load(); }

  load() {
    const params: any = {};
    if (this.actionType) params['actionType'] = this.actionType;
    this.api.get<any[]>('/api/platform/audit-logs', params).subscribe(res => this.logs.set(res.data || []));
  }

  filter() { this.load(); }
}
