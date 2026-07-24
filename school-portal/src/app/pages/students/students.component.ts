import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-students',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './students.component.html',
  styleUrl: './students.component.css'
})
export class StudentsComponent implements OnInit {
  students = signal<any[]>([]);
  allStudents: any[] = [];
  classes = signal<any[]>([]);
  sections = signal<any[]>([]);

  search = '';
  filterClass = '';
  filterSection = '';

  constructor(private api: ApiService, private auth: AuthService, private router: Router) {}

  ngOnInit() { this.load(); this.loadFilters(); }

  load() {
    this.api.get<any[]>('/api/school/students').subscribe(res => {
      this.allStudents = res.data || [];
      this.applyClientFilters();
    });
  }

  loadFilters() {
    this.api.get<any[]>('/api/school/classes').subscribe(res => this.classes.set(res.data || []));
    this.api.get<any[]>('/api/school/sections').subscribe(res => this.sections.set(res.data || []));
  }

  get activeStudentCount(): number { return this.allStudents.filter(s => s.status === 'ACTIVE').length; }

  applyClientFilters() {
    let list = this.allStudents;
    if (this.filterClass) list = list.filter(s => s.classId === this.filterClass);
    if (this.filterSection) list = list.filter(s => s.sectionId === this.filterSection);
    if (this.search.trim()) {
      const q = this.search.toLowerCase();
      list = list.filter(s =>
        s.name?.toLowerCase().includes(q) || s.firstName?.toLowerCase().includes(q) ||
        s.lastName?.toLowerCase().includes(q) || s.studentId?.toLowerCase().includes(q) ||
        s.parentEmail?.toLowerCase().includes(q)
      );
    }
    this.students.set(list);
  }

  getFilterBarSections(): any[] {
    if (!this.filterClass) return this.sections();
    return this.sections().filter(s => s.classId === this.filterClass);
  }

  onFilterClassChange() { this.filterSection = ''; this.applyClientFilters(); }
  goToStudent(id: string) { this.router.navigate(['/students', id]); }
}
