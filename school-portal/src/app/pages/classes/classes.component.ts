import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
@Component({ selector: 'app-classes', standalone: true, imports: [CommonModule, FormsModule, RouterLink], templateUrl: './classes.component.html', styleUrl: './classes.component.css' })
export class ClassesComponent implements OnInit {
  classes = signal<any[]>([]);
  sections = signal<any[]>([]);
  showClassForm = signal(false);
  showSectionForm = signal(false);
  user: any;
  name = ''; gradeLevel = '';
  sectionName = ''; sectionClassId = '';

  constructor(private api: ApiService, private auth: AuthService, private router: Router) { this.user = this.auth.currentUser; }
  ngOnInit() { this.load(); }
  load() {
    this.api.get<any[]>('/api/school/classes').subscribe(res => this.classes.set(res.data || []));
    this.api.get<any[]>('/api/school/sections').subscribe(res => this.sections.set(res.data || []));
  }
  createClass() {
    this.api.post('/api/school/classes', { name: this.name, gradeLevel: this.gradeLevel }).subscribe({
      next: () => { this.showClassForm.set(false); this.name=''; this.gradeLevel=''; this.load(); }, error: () => {}
    });
  }
  createSection() {
    this.api.post('/api/school/sections', { name: this.sectionName, classId: this.sectionClassId }).subscribe({
      next: () => { this.showSectionForm.set(false); this.sectionName=''; this.sectionClassId=''; this.load(); }, error: () => {}
    });
  }
  getSectionsForClass(classId: string) {
    return this.sections().filter((s: any) => s.classId === classId);
  }
  goToClass(id: string) { this.router.navigate(['/classes', id]); }
  logout() { this.auth.logout(); }
}
