import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-teachers',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './teachers.component.html',
  styleUrl: './teachers.component.css'
})
export class TeachersComponent implements OnInit {
  teachers = signal<any[]>([]);
  invitationLink = signal('');
  showInviteSuccess = signal(false);

  // Filters
  searchTerm = '';
  statusFilter = '';

  constructor(private api: ApiService, private auth: AuthService, private router: Router) {}

  ngOnInit() { this.load(); }

  load() { this.api.get<any[]>('/api/school/teachers').subscribe(res => this.teachers.set(res.data || [])); }

  get activeCount(): number { return this.teachers().filter(t => t.status === 'ACTIVE').length; }
  get pendingCount(): number { return this.teachers().filter(t => t.status === 'PENDING').length; }
  get deactivatedCount(): number { return this.teachers().filter(t => t.status === 'DEACTIVATED').length; }

  get filteredTeachers(): any[] {
    let list = this.teachers();
    if (this.statusFilter) {
      list = list.filter(t => t.status === this.statusFilter);
    }
    if (this.searchTerm.trim()) {
      const q = this.searchTerm.toLowerCase();
      list = list.filter(t =>
        t.name?.toLowerCase().includes(q) ||
        t.email?.toLowerCase().includes(q) ||
        t.specialization?.toLowerCase().includes(q) ||
        t.qualification?.toLowerCase().includes(q) ||
        t.employeeId?.toLowerCase().includes(q) ||
        t.phone?.includes(q)
      );
    }
    return list;
  }

  applyFilters() {} // Triggers getter re-evaluation via template binding

  goToTeacher(id: string) { this.router.navigate(['/teachers', id]); }
  copyInviteLink() { navigator.clipboard.writeText(this.invitationLink()); }
  closeInviteSuccess() { this.showInviteSuccess.set(false); }
}
