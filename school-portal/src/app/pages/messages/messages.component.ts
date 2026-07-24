import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-messages',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './messages.component.html',
  styleUrl: './messages.component.css'
})
export class MessagesComponent implements OnInit {
  threads = signal<any[]>([]);
  messages = signal<any[]>([]);
  recipients = signal<any[]>([]);
  selectedThread = signal<any>(null);
  showCompose = signal(false);
  replyText = '';
  user: any;

  selectedRecipient: any = null;
  composeSubject = '';
  composeBody = '';
  composeError = signal('');

  constructor(private api: ApiService, private auth: AuthService) { this.user = this.auth.currentUser; }

  ngOnInit() { this.load(); }
  load() { this.api.get<any[]>('/api/school/messages').subscribe(res => this.threads.set(res.data || [])); }

  isMe(msg: any): boolean {
    return msg.isMe === true;
  }

  formatTime(dateStr: string): string {
    if (!dateStr) return '';
    const date = new Date(dateStr);
    if (isNaN(date.getTime())) return dateStr;
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    if (diffMins < 1) return 'Just now';
    if (diffMins < 60) return diffMins + 'm ago';
    const diffHours = Math.floor(diffMins / 60);
    if (diffHours < 24) return diffHours + 'h ago';
    const diffDays = Math.floor(diffHours / 24);
    if (diffDays < 7) return diffDays + 'd ago';
    return date.toLocaleDateString('en-IN', { day: 'numeric', month: 'short' });
  }

  openCompose() {
    this.showCompose.set(true);
    this.composeError.set('');
    this.api.get<any[]>('/api/school/message-recipients').subscribe(res => this.recipients.set(res.data || []));
  }

  sendMessage() {
    if (!this.selectedRecipient || !this.composeSubject.trim() || !this.composeBody.trim()) {
      this.composeError.set('Please fill all fields'); return;
    }
    this.api.post('/api/school/messages', {
      parentId: this.selectedRecipient.parentId, studentId: this.selectedRecipient.studentId,
      subject: this.composeSubject, body: this.composeBody
    }).subscribe({
      next: () => { this.showCompose.set(false); this.selectedRecipient = null; this.composeSubject = ''; this.composeBody = ''; this.load(); },
      error: (err) => this.composeError.set(err.error?.errors?.[0]?.message || 'Failed')
    });
  }

  openThread(thread: any) {
    this.selectedThread.set(thread);
    this.api.get<any[]>(`/api/school/messages/${thread.id}`).subscribe(res => this.messages.set(res.data || []));
  }

  sendReply() {
    if (!this.replyText.trim() || !this.selectedThread()) return;
    this.api.post(`/api/school/messages/${this.selectedThread().id}/replies`, { body: this.replyText }).subscribe({
      next: () => { this.replyText = ''; this.openThread(this.selectedThread()); },
      error: () => {}
    });
  }
}
