import { Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { TranslatePipe } from '@ngx-translate/core';

@Component({
  selector: 'app-dialog-shell',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, TranslatePipe],
  templateUrl: './dialog-shell.component.html',
  styleUrl: './dialog-shell.component.scss',
})
export class DialogShellComponent {
  readonly title = input.required<string>();
  readonly close = output<void>();
}
