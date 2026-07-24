import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-messages',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="messages-page">
      <div class="page-header">
        <h1>💬 Messages</h1>
        <button (click)="openCompose()" class="btn-compose">+ New Message</button>
      </div>

      @if (showCompose()) {
        <div class="compose-overlay" (click)="showCompose.set(false)">
          <div class="compose-modal" (click)="$event.stopPropagation()">
            <h3>New Message to Teacher</h3>
            @if (composeError()) { <div class="compose-error">{{ composeError() }}</div> }
            <div class="compose-form">
              <div class="field">
                <label>Teacher & Student</label>
                <select [(ngModel)]="selectedRecipient" class="field-input">
                  <option [ngValue]="null">Select a teacher...</option>
                  @for (t of teachers(); track t.teacherId + t.studentId) {
                    <option [ngValue]="t">{{ t.teacherName }} — Re: {{ t.studentName }}</option>
                  }
                </select>
              </div>
              <div class="field">
                <label>Subject</label>
                <input [(ngModel)]="composeSubject" placeholder="What is this about?" class="field-input" />
              </div>
              <div class="field">
                <label>Message</label>
                <textarea [(ngModel)]="composeBody" placeholder="Type your message..." rows="4" class="field-input textarea"></textarea>
              </div>
              <div class="compose-actions">
                <button (click)="showCompose.set(false)" class="btn-cancel">Cancel</button>
                <button (click)="sendNewMessage()" class="btn-send">Send</button>
              </div>
            </div>
          </div>
        </div>
      }

      <div class="messages-layout">
        <div class="thread-list">
          <div class="list-header">Conversations</div>
          @for (thread of threads(); track thread.id) {
            <div class="thread-item" [class.active]="selectedThread()?.id === thread.id" (click)="selectThread(thread)">
              <div class="thread-sender">{{ thread.staffName }}</div>
              <div class="thread-subject">{{ thread.subject }}</div>
              <div class="thread-student">About: {{ thread.studentName }}</div>
            </div>
          } @empty {
            <div class="empty-state">No messages yet. Start a conversation with your child's teacher.</div>
          }
        </div>

        <div class="thread-detail">
          @if (selectedThread()) {
            <div class="detail-header">
              <h3>{{ selectedThread()?.subject }}</h3>
              <span class="detail-meta">with {{ selectedThread()?.staffName }} about {{ selectedThread()?.studentName }}</span>
            </div>
            <div class="messages-container">
              @for (msg of messages(); track msg.id) {
                <div class="bubble-row" [class.mine]="msg.isMe">
                  <div class="bubble" [class.bubble-mine]="msg.isMe" [class.bubble-them]="!msg.isMe">
                    <div class="bubble-sender">{{ msg.senderName }}</div>
                    <div class="bubble-body">{{ msg.body }}</div>
                    <div class="bubble-time">{{ msg.createdAt }}</div>
                  </div>
                </div>
              }
              @empty {
                <div class="empty-state">No messages in this thread.</div>
              }
            </div>
            <div class="reply-bar">
              <input [(ngModel)]="replyText" placeholder="Type your reply..." (keyup.enter)="sendReply()" class="reply-input" />
              <button (click)="sendReply()" [disabled]="!replyText.trim()" class="btn-reply">Send</button>
            </div>
          } @else {
            <div class="no-selection">Select a conversation to view messages</div>
          }
        </div>
      </div>
    </div>
  `,
  styles: [`
    .messages-page { }
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
    h1 { font-size: 22px; margin: 0; }
    .btn-compose { padding: 10px 20px; background: #4a6b8a; color: #fff; border: none; border-radius: 8px; cursor: pointer; font-weight: 600; }
    .btn-compose:hover { background: #33506b; }

    .messages-layout { display: grid; grid-template-columns: 300px 1fr; gap: 16px; height: calc(100vh - 200px); }

    .thread-list { background: #fff; border-radius: 12px; overflow-y: auto; box-shadow: 0 1px 3px rgba(0,0,0,0.06); }
    .list-header { padding: 14px 16px; font-weight: 600; font-size: 14px; border-bottom: 1px solid #eee; color: #555; }
    .thread-item { padding: 14px 16px; border-bottom: 1px solid #f4f4f4; cursor: pointer; transition: background 0.15s; }
    .thread-item:hover { background: #f8f9fb; }
    .thread-item.active { background: #e8eef3; border-left: 3px solid #4a6b8a; }
    .thread-sender { font-size: 13px; font-weight: 600; }
    .thread-subject { font-size: 13px; color: #333; margin-top: 2px; }
    .thread-student { font-size: 11px; color: #888; margin-top: 3px; }

    .thread-detail { background: #fff; border-radius: 12px; display: flex; flex-direction: column; box-shadow: 0 1px 3px rgba(0,0,0,0.06); overflow: hidden; }
    .detail-header { padding: 16px 20px; border-bottom: 1px solid #eee; }
    .detail-header h3 { font-size: 15px; margin: 0 0 4px; }
    .detail-meta { font-size: 12px; color: #888; }

    .messages-container { flex: 1; padding: 20px; overflow-y: auto; display: flex; flex-direction: column; gap: 12px; }
    .bubble-row { display: flex; }
    .bubble-row.mine { justify-content: flex-end; }
    .bubble { max-width: 75%; padding: 12px 16px; border-radius: 16px; }
    .bubble-them { background: #f0f2f5; border-bottom-left-radius: 4px; }
    .bubble-mine { background: #4a6b8a; color: #fff; border-bottom-right-radius: 4px; }
    .bubble-sender { font-size: 11px; font-weight: 600; margin-bottom: 4px; opacity: 0.7; }
    .bubble-mine .bubble-sender { color: rgba(255,255,255,0.7); }
    .bubble-body { font-size: 14px; line-height: 1.5; word-wrap: break-word; }
    .bubble-time { font-size: 10px; margin-top: 6px; opacity: 0.5; }

    .reply-bar { display: flex; gap: 10px; padding: 14px 20px; border-top: 1px solid #eee; }
    .reply-input { flex: 1; padding: 12px 16px; border: 1px solid #ddd; border-radius: 10px; font-size: 14px; outline: 0; }
    .reply-input:focus { border-color: #4a6b8a; }
    .btn-reply { padding: 12px 24px; background: #4a6b8a; color: #fff; border: none; border-radius: 10px; cursor: pointer; font-weight: 600; }
    .btn-reply:disabled { opacity: 0.5; cursor: not-allowed; }

    .no-selection { display: flex; align-items: center; justify-content: center; height: 100%; color: #aaa; }
    .empty-state { padding: 32px; text-align: center; color: #aaa; font-size: 14px; }

    .compose-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center; z-index: 200; }
    .compose-modal { background: #fff; border-radius: 16px; padding: 32px; width: 500px; max-width: 90vw; }
    .compose-modal h3 { margin: 0 0 20px; font-size: 1.1rem; }
    .compose-form { display: flex; flex-direction: column; gap: 16px; }
    .field { display: flex; flex-direction: column; gap: 6px; }
    .field label { font-size: 13px; font-weight: 600; color: #555; }
    .field-input { padding: 12px 14px; border: 1px solid #ddd; border-radius: 10px; font-size: 14px; outline: 0; font-family: inherit; }
    .field-input:focus { border-color: #4a6b8a; }
    .textarea { resize: vertical; min-height: 80px; }
    .compose-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 8px; }
    .btn-cancel { padding: 10px 20px; background: #fff; border: 1px solid #ddd; border-radius: 8px; cursor: pointer; }
    .btn-send { padding: 10px 20px; background: #4a6b8a; color: #fff; border: none; border-radius: 8px; cursor: pointer; font-weight: 600; }
    .compose-error { background: #fbeaea; color: #b0473f; padding: 10px 14px; border-radius: 8px; font-size: 13px; }
  `]
})
export class MessagesComponent implements OnInit {
  threads = signal<any[]>([]);
  selectedThread = signal<any>(null);
  messages = signal<any[]>([]);
  teachers = signal<any[]>([]);
  showCompose = signal(false);
  replyText = '';
  selectedRecipient: any = null;
  composeSubject = '';
  composeBody = '';
  composeError = signal('');

  constructor(private api: ApiService, private auth: AuthService) {}

  ngOnInit() { this.loadThreads(); }

  loadThreads() {
    this.api.get<any[]>('/api/parent/messages').subscribe(res => this.threads.set(res.data || []));
  }

  selectThread(thread: any) {
    this.selectedThread.set(thread);
    this.api.get<any[]>(`/api/parent/messages/${thread.id}`).subscribe(res => this.messages.set(res.data || []));
  }

  sendReply() {
    const thread = this.selectedThread();
    if (!thread || !this.replyText.trim()) return;
    this.api.post(`/api/parent/messages/${thread.id}/replies`, { body: this.replyText }).subscribe({
      next: () => { this.replyText = ''; this.selectThread(thread); }
    });
  }

  openCompose() {
    this.showCompose.set(true);
    this.composeError.set('');
    this.api.get<any[]>('/api/parent/teachers').subscribe(res => this.teachers.set(res.data || []));
  }

  sendNewMessage() {
    if (!this.selectedRecipient || !this.composeSubject.trim() || !this.composeBody.trim()) {
      this.composeError.set('Please fill all fields');
      return;
    }
    this.api.post('/api/parent/messages', {
      staffId: this.selectedRecipient.teacherId,
      studentId: this.selectedRecipient.studentId,
      subject: this.composeSubject,
      body: this.composeBody
    }).subscribe({
      next: () => { this.showCompose.set(false); this.composeSubject = ''; this.composeBody = ''; this.selectedRecipient = null; this.loadThreads(); },
      error: (err) => this.composeError.set(err.error?.errors?.[0]?.message || 'Failed to send')
    });
  }
}
