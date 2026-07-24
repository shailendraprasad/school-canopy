import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-team-members',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './team-members.component.html',
  styleUrl: './team-members.component.css'
})
export class TeamMembersComponent implements OnInit {
  members = signal<any[]>([]);
  schools = signal<any[]>([]);
  showForm = signal(false);
  showAssignForm = signal(false);
  name = '';
  email = '';
  formError = signal('');
  formLoading = signal(false);

  // Invitation link
  invitationLink = signal('');
  showInviteSuccess = signal(false);

  // School assignment
  assignMemberId = '';
  assignMemberName = '';
  selectedSchoolIds: string[] = [];

  constructor(private api: ApiService, private auth: AuthService) {}

  ngOnInit() { this.load(); this.loadSchools(); }

  load() {
    this.api.get<any[]>('/api/platform/team-members').subscribe(res => this.members.set(res.data || []));
  }

  loadSchools() {
    this.api.get<any[]>('/api/platform/schools').subscribe(res => {
      const schools = (res.data || []).map((s: any) => ({ ...s, id: s.id?.toString() || s.id }));
      this.schools.set(schools);
    });
  }

  onSubmit() {
    this.formError.set('');
    this.formLoading.set(true);
    this.api.post<any>('/api/platform/team-members', { name: this.name, email: this.email }).subscribe({
      next: (res) => {
        this.formLoading.set(false);
        this.showForm.set(false);
        this.name = '';
        this.email = '';
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

  toggleStatus(m: any) {
    const s = m.status === 'ACTIVE' ? 'DEACTIVATED' : 'ACTIVE';
    this.api.patch(`/api/platform/team-members/${m.id}/status`, { status: s }).subscribe(() => this.load());
  }

  openAssignSchools(member: any) {
    this.assignMemberId = member.id;
    this.assignMemberName = member.name;
    this.selectedSchoolIds = (member.assignedSchools || []).map((s: any) => s.id.toString());
    this.showAssignForm.set(true);
  }

  toggleSchool(schoolId: string) {
    const idx = this.selectedSchoolIds.indexOf(schoolId);
    if (idx >= 0) {
      this.selectedSchoolIds.splice(idx, 1);
    } else {
      this.selectedSchoolIds.push(schoolId);
    }
  }

  isSchoolSelected(schoolId: string): boolean {
    return this.selectedSchoolIds.includes(schoolId);
  }

  saveSchoolAssignment() {
    this.api.put(`/api/platform/team-members/${this.assignMemberId}/schools`, { schoolIds: this.selectedSchoolIds }).subscribe({
      next: () => { this.showAssignForm.set(false); this.load(); },
      error: () => {}
    });
  }
}
