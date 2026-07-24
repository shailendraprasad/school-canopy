import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-staff',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './staff.component.html',
  styleUrl: './staff.component.css'
})
export class StaffComponent implements OnInit {
  staff = signal<any[]>([]);
  searchTerm = '';
  statusFilter = '';

  constructor(private api: ApiService, private router: Router) {}

  ngOnInit() { this.load(); }
  load() { this.api.get<any[]>('/api/school/office-staff').subscribe(res => this.staff.set(res.data || [])); }

  get activeCount(): number { return this.staff().filter(s => s.status === 'ACTIVE').length; }
  get pendingCount(): number { return this.staff().filter(s => s.status === 'PENDING').length; }

  get filteredStaff(): any[] {
    let list = this.staff();
    if (this.statusFilter) list = list.filter(s => s.status === this.statusFilter);
    if (this.searchTerm.trim()) {
      const q = this.searchTerm.toLowerCase();
      list = list.filter(s => s.name?.toLowerCase().includes(q) || s.email?.toLowerCase().includes(q) || s.designation?.toLowerCase().includes(q) || s.phone?.includes(q));
    }
    return list;
  }

  applyFilters() {}
  goToStaff(id: string) { this.router.navigate(['/staff', id]); }
}
