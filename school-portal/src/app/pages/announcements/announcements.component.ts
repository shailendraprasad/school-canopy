import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-announcements',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './announcements.component.html',
  styleUrl: './announcements.component.css'
})
export class AnnouncementsComponent implements OnInit {
  allAnnouncements: any[] = [];
  announcements = signal<any[]>([]);
  classes = signal<any[]>([]);
  sections = signal<any[]>([]);
  showForm = signal(false);
  user: any;

  // Create form fields
  title = '';
  body = '';
  category = 'GENERAL';
  scopeType = 'SCHOOL';
  scopeId = '';
  selectedClassId = '';

  // Filter fields
  filterScope = '';
  filterClassId = '';
  filterScopeId = '';
  filterCategory = '';
  expandedId = '';

  constructor(private api: ApiService, private auth: AuthService) { this.user = this.auth.currentUser; }

  ngOnInit() { this.load(); this.loadFilters(); }

  load() {
    this.api.get<any[]>('/api/school/announcements').subscribe(res => {
      this.allAnnouncements = res.data || [];
      this.applyFilter();
    });
  }

  loadFilters() {
    this.api.get<any[]>('/api/school/classes').subscribe(res => this.classes.set(res.data || []));
    this.api.get<any[]>('/api/school/sections').subscribe(res => this.sections.set(res.data || []));
  }

  applyFilter() {
    let list = this.allAnnouncements;
    if (this.filterScope) {
      list = list.filter(a => a.scopeType === this.filterScope);
    }
    if (this.filterCategory) {
      list = list.filter(a => a.category === this.filterCategory);
    }
    if (this.filterScope === 'CLASS' && this.filterClassId) {
      list = list.filter(a => a.scopeId === this.filterClassId);
    }
    if (this.filterScope === 'SECTION' && this.filterClassId && !this.filterScopeId) {
      // Filter by all sections belonging to this class
      const classSectionIds = this.sections().filter(s => s.classId === this.filterClassId).map(s => s.id);
      list = list.filter(a => classSectionIds.includes(a.scopeId));
    }
    if (this.filterScope === 'SECTION' && this.filterScopeId) {
      list = list.filter(a => a.scopeId === this.filterScopeId);
    }
    this.announcements.set(list);
  }

  toggleExpand(id: string) {
    this.expandedId = this.expandedId === id ? '' : id;
  }

  onFilterScopeChange() {
    this.filterClassId = '';
    this.filterScopeId = '';
    this.applyFilter();
  }

  onFilterClassChange() {
    this.filterScopeId = '';
    this.applyFilter();
  }

  // Create form methods
  onScopeTypeChange() {
    this.scopeId = '';
    this.selectedClassId = '';
  }

  onClassChange() {
    this.scopeId = '';
  }

  getFilteredSections(): any[] {
    if (!this.selectedClassId) return [];
    return this.sections().filter(s => s.classId === this.selectedClassId);
  }

  getFilteredSectionsFor(classId: string): any[] {
    if (!classId) return [];
    return this.sections().filter(s => s.classId === classId);
  }

  getSectionDisplayName(section: any): string {
    const cls = this.classes().find(c => c.id === section.classId);
    return cls ? `${cls.name} - ${section.name}` : section.name;
  }

  getBodyPreview(body: string): string {
    if (!body) return '';
    return body.length > 120 ? body.substring(0, 120) + '...' : body;
  }

  onSubmit() {
    const payload: any = {
      title: this.title,
      body: this.body,
      category: this.category,
      scopeType: this.scopeType,
      status: 'PUBLISHED'
    };
    if (this.scopeType === 'CLASS' && this.selectedClassId) {
      payload.scopeId = this.selectedClassId;
    } else if (this.scopeType === 'SECTION' && this.scopeId) {
      payload.scopeId = this.scopeId;
    }
    this.api.post('/api/school/announcements', payload).subscribe({
      next: () => {
        this.showForm.set(false);
        this.title = '';
        this.body = '';
        this.category = 'GENERAL';
        this.scopeType = 'SCHOOL';
        this.scopeId = '';
        this.selectedClassId = '';
        this.load();
      },
      error: () => {}
    });
  }
}
