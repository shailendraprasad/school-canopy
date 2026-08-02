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

  editingId = signal<string | null>(null);

  selectedAnnouncement = signal<any | null>(null);

  formError = signal('');

  deleteConfirmId = signal<string | null>(null);

  user: any;



  title = '';

  body = '';

  category = 'GENERAL';

  scopeType = 'SCHOOL';

  scopeId = '';

  selectedClassId = '';



  filterScope = '';

  filterClassId = '';

  filterScopeId = '';

  filterCategory = '';



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

    if (this.filterScope) list = list.filter(a => a.scopeType === this.filterScope);

    if (this.filterCategory) list = list.filter(a => a.category === this.filterCategory);

    if (this.filterScope === 'CLASS' && this.filterClassId) {

      list = list.filter(a => a.scopeId === this.filterClassId);

    }

    if (this.filterScope === 'SECTION' && this.filterClassId && !this.filterScopeId) {

      const classSectionIds = this.sections().filter(s => s.classId === this.filterClassId).map(s => s.id);

      list = list.filter(a => classSectionIds.includes(a.scopeId));

    }

    if (this.filterScope === 'SECTION' && this.filterScopeId) {

      list = list.filter(a => a.scopeId === this.filterScopeId);

    }

    this.announcements.set(list);

  }



  openDetail(a: any, event?: Event) {

    event?.stopPropagation();

    this.selectedAnnouncement.set(a);

  }



  closeDetail() {

    this.selectedAnnouncement.set(null);

    this.deleteConfirmId.set(null);

  }



  openCreateForm() {

    this.resetForm();

    this.editingId.set(null);

    this.showForm.set(true);

  }



  openEditForm(a: any) {

    this.editingId.set(a.id);

    this.title = a.title;

    this.body = a.body;

    this.category = a.category || 'GENERAL';

    this.scopeType = a.scopeType || 'SCHOOL';

    this.selectedClassId = a.scopeClassId || (a.scopeType === 'CLASS' ? a.scopeId : '') || '';

    this.scopeId = a.scopeType === 'SECTION' ? (a.scopeId || '') : '';

    this.formError.set('');

    this.selectedAnnouncement.set(null);

    this.showForm.set(true);

  }



  resetForm() {

    this.title = '';

    this.body = '';

    this.category = 'GENERAL';

    this.scopeType = 'SCHOOL';

    this.scopeId = '';

    this.selectedClassId = '';

    this.formError.set('');

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



  getAudiencePreview(): string {

    if (this.scopeType === 'SCHOOL') return 'All parents in the school will see this announcement.';

    if (this.scopeType === 'CLASS') {

      const cls = this.classes().find(c => c.id === this.selectedClassId);

      return cls ? `Parents of students in ${cls.name} will see this.` : 'Select a class to target parents.';

    }

    if (this.scopeType === 'SECTION') {

      const sec = this.sections().find(s => s.id === this.scopeId);

      const cls = this.classes().find(c => c.id === this.selectedClassId);

      return sec && cls ? `Parents of students in ${cls.name} · Section ${sec.name} will see this.` : 'Select class and section to target parents.';

    }

    return '';

  }



  getScopeLabel(a: any): string {

    return a.scopeLabel || a.scopeType;

  }



  getCategoryLabel(category: string): string {

    const labels: Record<string, string> = {

      GENERAL: 'General',

      EVENTS: 'Events',

      ACTION_NEEDED: 'Action Needed'

    };

    return labels[category] || category;

  }



  getBodyPreview(body: string): string {

    if (!body) return '';

    return body.length > 120 ? body.substring(0, 120) + '...' : body;

  }



  onSubmit() {

    this.formError.set('');

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



    const editing = this.editingId();

    const request = editing

      ? this.api.patch(`/api/school/announcements/${editing}`, payload)

      : this.api.post('/api/school/announcements', payload);



    request.subscribe({

      next: () => {

        this.showForm.set(false);

        this.editingId.set(null);

        this.resetForm();

        this.load();

      },

      error: (err) => this.formError.set(err.error?.errors?.[0]?.message || 'Failed to save announcement')

    });

  }



  confirmDelete(a: any) {

    this.deleteConfirmId.set(a.id);

  }



  cancelDelete() {

    this.deleteConfirmId.set(null);

  }



  deleteAnnouncement(id: string) {

    this.api.delete(`/api/school/announcements/${id}`).subscribe({

      next: () => {

        this.closeDetail();

        this.load();

      },

      error: (err) => this.formError.set(err.error?.errors?.[0]?.message || 'Failed to delete announcement')

    });

  }

}
