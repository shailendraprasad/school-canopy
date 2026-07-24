import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-school-admins',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './school-admins.component.html',
  styleUrl: './school-admins.component.css'
})
export class SchoolAdminsComponent implements OnInit {
  admins = signal<any[]>([]);
  schools = signal<any[]>([]);
  showForm = signal(false);
  name = '';
  email = '';
  schoolId = '';
  formError = signal('');
  formLoading = signal(false);

  // Invitation link
  invitationLink = signal('');
  showInviteSuccess = signal(false);

  constructor(private api: ApiService, private auth: AuthService) {}

  ngOnInit() { this.load(); this.loadSchools(); }

  load() {
    this.api.get<any[]>('/api/platform/school-admins').subscribe(res => this.admins.set(res.data || []));
  }

  loadSchools() {
    this.api.get<any[]>('/api/platform/schools').subscribe(res => this.schools.set(res.data || []));
  }

  onSubmit() {
    this.formError.set('');
    this.formLoading.set(true);
    this.api.post<any>('/api/platform/school-admins', { name: this.name, email: this.email, schoolId: this.schoolId }).subscribe({
      next: (res) => {
        this.formLoading.set(false);
        this.showForm.set(false);
        this.name = '';
        this.email = '';
        this.schoolId = '';
        // Show invitation link
        const token = res.data?.invitationToken;
        if (token) {
          const link = `${window.location.origin}/setup/${token}`;
          this.invitationLink.set(link);
          this.showInviteSuccess.set(true);
        }
        this.load();
      },
      error: (err) => { this.formLoading.set(false); this.formError.set(err.error?.errors?.[0]?.message || 'Failed'); }
    });
  }

  copyInviteLink() {
    navigator.clipboard.writeText(this.invitationLink());
  }

  closeInviteSuccess() {
    this.showInviteSuccess.set(false);
    this.invitationLink.set('');
  }

  toggleStatus(a: any) {
    const s = a.status === 'ACTIVE' ? 'DEACTIVATED' : 'ACTIVE';
    this.api.patch(`/api/platform/school-admins/${a.id}/status`, { status: s }).subscribe(() => this.load());
  }
}
