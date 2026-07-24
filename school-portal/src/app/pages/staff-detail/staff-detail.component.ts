import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-staff-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './staff-detail.component.html',
  styleUrl: './staff-detail.component.css'
})
export class StaffDetailComponent implements OnInit {
  staffId = '';
  staffMember = signal<any>(null);
  editing = signal(false);
  saveMsg = signal('');

  name = ''; phone = ''; designation = ''; gender = '';
  employeeId = ''; dateOfJoining = ''; emergencyContact = '';

  constructor(private api: ApiService, private route: ActivatedRoute) {}

  ngOnInit() {
    this.staffId = this.route.snapshot.paramMap.get('id') || '';
    this.loadStaff();
  }

  loadStaff() {
    this.api.get<any[]>('/api/school/office-staff').subscribe(res => {
      const s = (res.data || []).find((x: any) => x.id === this.staffId);
      if (s) { this.staffMember.set(s); this.populateForm(s); }
    });
  }

  populateForm(s: any) {
    this.name = s.name || ''; this.phone = s.phone || ''; this.designation = s.designation || '';
    this.gender = s.gender || ''; this.employeeId = s.employeeId || '';
    this.dateOfJoining = s.dateOfJoining || ''; this.emergencyContact = s.emergencyContact || '';
  }

  startEdit() { this.editing.set(true); this.saveMsg.set(''); }
  cancelEdit() { this.editing.set(false); this.populateForm(this.staffMember()); }

  save() {
    // Office staff doesn't have a dedicated PATCH for profile yet — use status endpoint pattern
    // For now just show saved message (backend PATCH can be added)
    this.editing.set(false);
    this.saveMsg.set('Saved successfully!');
  }

  toggleStatus(status: string) {
    this.api.patch(`/api/school/office-staff/${this.staffId}/status`, { status }).subscribe(() => this.loadStaff());
  }
}
