import { Component } from '@angular/core';
import { IconComponent, IconName } from '../../components/icon.component';
import { BudgetFormComponent } from '../../components/budget-form.component';
import { COMPANY } from '../../core/company';
import { RevealDirective } from '../../core/reveal.directive';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [IconComponent, BudgetFormComponent, RevealDirective],
  templateUrl: './home.component.html',
  styleUrl: './home.component.css'
})
export class HomeComponent {
  readonly company = COMPANY;
  readonly services: { number: string; icon: IconName; title: string; description: string; detail: string }[] = [
    { number: '01', icon: 'tools', title: 'Assistência técnica', description: 'Cuidado para seu computador continuar acompanhando você.', detail: 'Manutenção, formatação, upgrades, limpeza e recuperação.' },
    { number: '02', icon: 'monitor', title: 'Equipamentos e acessórios', description: 'Tecnologia para trabalhar, estudar e aproveitar o dia a dia.', detail: 'PCs, notebooks, monitores, teclados, mouses e cabos.' },
    { number: '03', icon: 'printer', title: 'Cartuchos e toner', description: 'Sua rotina de impressão também tem lugar aqui.', detail: 'Recarga de cartuchos e toner para suas necessidades de impressão.' },
    { number: '04', icon: 'support', title: 'Suporte perto de você', description: 'Ajuda com a tecnologia, onde você precisar.', detail: 'Atendimento técnico presencial e online.' },
    { number: '05', icon: 'rental', title: 'Locação de equipamentos', description: 'Uma alternativa à compra para a sua necessidade.', detail: 'Locação de computadores e impressoras. Consulte as opções.' },
    { number: '06', icon: 'shield', title: 'Manutenção preventiva', description: 'Inclua o cuidado com os equipamentos na sua rotina.', detail: 'Consulte nossos planos de manutenção preventiva.' }
  ];
}
