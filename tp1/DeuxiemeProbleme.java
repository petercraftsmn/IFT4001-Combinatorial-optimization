import org.chocosolver.solver.constraints.*;
import org.chocosolver.solver.constraints.extension.Tuples;
import org.chocosolver.solver.constraints.nary.cnf.LogOp;
import org.chocosolver.solver.search.strategy.*;
import org.chocosolver.solver.variables.*;
import org.chocosolver.solver.*;
import org.chocosolver.solver.trace.*;
import org.chocosolver.solver.search.limits.*;
import org.chocosolver.solver.search.loop.monitors.*;

public class DeuxiemeProbleme {
	public static final int NOMBRE_HEURES_MINIMUM = 5;
	public static final int NOMBRE_HEURES_MAXIMUM = 7;
	public static final int NOMBRE_EMPLOYES_MINIMUM = 1;
	public static final int MULTIPLE_PERTE = 20;

	public static final int HEURISTIQUE_DEFAUT = 0;
	public static final int HEURISTIQUE_DOMOVERWDEG = 1;
	public static final int HEURISTIQUE_IMPACT_BASED_SEARCH = 2;

	public static final int RESTART_AUCUN = 0;
	public static final int RESTART_LUBY = 1;
	public static final int RESTART_GEOMETRIQUE = 2;

	public static final int COHERENCE_DE_BORNES = 0;
	public static final int COHERENCE_DE_DOMAINE = 1;

	public static void main(String[] args) {
		final int p = 16;
		final int n = 5;

		final int coherence = COHERENCE_DE_BORNES;
		final int heuristique = HEURISTIQUE_DEFAUT;
		final int restart = RESTART_AUCUN;
		final boolean bris_symetries = false;

		// Creation du solveur
		Solver solver = new Solver();

		// Creation d'une matrice de dimensions n x p de variables dont les
		// domaines sont les entiers de 0 ou 1 et des variables pour la
		// minimization.
		BoolVar[][] lignes = VariableFactory.boolMatrix("x", n, p, solver);
		IntVar[] offre;
		IntVar[] demande;
		IntVar[] perteTemp;
		IntVar[] perte;
		IntVar perteTotal;
		if (coherence == COHERENCE_DE_BORNES) {
			offre = VariableFactory.boundedArray("s", p, 0, n, solver);
			demande = VariableFactory.boundedArray("d", p, 0, n, solver);
			perteTemp = VariableFactory.boundedArray("t", p, 0, n, solver);
			perte = VariableFactory.boundedArray("p", p, 0, n * MULTIPLE_PERTE, solver);
			perteTotal = VariableFactory.bounded("m", 0, p * n * MULTIPLE_PERTE, solver);
		} else {
			offre = VariableFactory.enumeratedArray("o", p, 0, n, solver);
			demande = VariableFactory.enumeratedArray("d", p, 0, n, solver);
			perteTemp = VariableFactory.enumeratedArray("t", p, 0, n, solver);
			perte = VariableFactory.enumeratedArray("p", p, 0, n * MULTIPLE_PERTE, solver);
			perteTotal = VariableFactory.enumerated("m", 0, p * n * MULTIPLE_PERTE, solver);
		}

		// Creation de la tranpose de la matrice lignes.
		BoolVar[][] colonnes = new BoolVar[p][n];
		for (int i = 0; i < p; i++) {
			for (int j = 0; j < n; j++) {
				colonnes[i][j] = lignes[j][i];
			}
		}

		// Assignation de l'offre et de la demande et de la perte
		IntVar variableMultiplePerte = VariableFactory.fixed(MULTIPLE_PERTE, solver);
		for (int i = 0; i < p; i++) {
			solver.post(IntConstraintFactory.sum(colonnes[i], offre[i]));
			demande[i] = VariableFactory.fixed(nombreEmployesSouhaite(i), solver);
			solver.post(IntConstraintFactory.distance(offre[i], demande[i], "=", perteTemp[i]));
			solver.post(IntConstraintFactory.eucl_div(perte[i], variableMultiplePerte, perteTemp[i]));
		}

		// On trouve la perte totale
		solver.post(IntConstraintFactory.sum(perte, perteTotal));

		// Un employe doit travailler entre 5 et 7 heures.
		IntVar variableNombreDemiHeuresMinimum = VariableFactory.fixed(NOMBRE_HEURES_MINIMUM * 2 - 1, solver);
		IntVar variableNombreDemiHeuresMaximum = VariableFactory.fixed(NOMBRE_HEURES_MAXIMUM * 2 - 1, solver);
		for (int i = 0; i < n; i++) {
			solver.post(IntConstraintFactory.sum(lignes[i], ">=", variableNombreDemiHeuresMinimum));
			solver.post(IntConstraintFactory.sum(lignes[i], "<=", variableNombreDemiHeuresMaximum));
		}

		// Il doit toujours y avoir au moins un employe
		for (int i = 0; i < p; i++) {
			solver.post(IntConstraintFactory.arithm(offre[i], ">=", NOMBRE_EMPLOYES_MINIMUM));
		}

		// Cr�ation de A et de B
		BoolVar[][] A = VariableFactory.boolMatrix("a", n, p, solver);
		BoolVar[][] B = VariableFactory.boolMatrix("b", n, p, solver);
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < p - 1; j++) {
				solver.post(LogicalConstraintFactory.or(A[i][j].not(), A[i][j + 1]));
				solver.post(LogicalConstraintFactory.or(B[i][j], B[i][j + 1].not()));
			}
		}

		// On ajoute les contraintes pour les bloques
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < p; j++) {
				solver.post(LogicalConstraintFactory.or(A[i][j], lignes[i][j].not()));
				solver.post(LogicalConstraintFactory.or(B[i][j], lignes[i][j].not()));
				solver.post(LogicalConstraintFactory.or(A[i][j].not(), B[i][j].not(), lignes[i][j]));
			}
		}

		// Vecteur contenant toutes les variables de la matrice dans un seul
		// vecteur
		BoolVar[] toutesLesVariables = new BoolVar[n * p];
		for (int i = 0; i < n * p; i++) {
			toutesLesVariables[i] = lignes[i / p][i % p];
		}

		if (bris_symetries) {
			for (int i = 1; i < n / 2; i++)
				solver.post(IntConstraintFactory.arithm(lignes[i - 1][i - 1], "<", lignes[i][i]));

			solver.post(IntConstraintFactory.arithm(lignes[0][0], "<", lignes[n - 1][0]));
			solver.post(IntConstraintFactory.arithm(lignes[0][0], "<", lignes[0][n - 1]));
			solver.post(IntConstraintFactory.arithm(lignes[0][0], "<", lignes[n - 1][n - 1]));
			// solver.post(IntConstraintFactory.arithm(lignes[n - 1][0], "<",
			// lignes[0][n - 1]));
		}

		switch (heuristique) {
		case HEURISTIQUE_DOMOVERWDEG:
			solver.set(IntStrategyFactory.domOverWDeg(toutesLesVariables, 42));
			break;
		case HEURISTIQUE_IMPACT_BASED_SEARCH:
			solver.set(IntStrategyFactory.impact(toutesLesVariables, 42));
			break;
		}

		switch (restart) {
		case RESTART_LUBY:
			SearchMonitorFactory.luby(solver, 2, 2, new FailCounter(solver, 2), 25000);
			break;
		case RESTART_GEOMETRIQUE:
			SearchMonitorFactory.geometrical(solver, 2, 2.1, new FailCounter(solver, 2), 25000);
			break;
		}

		solver.findOptimalSolution(ResolutionPolicy.MINIMIZE, perteTotal);

		// Horaire
		System.out.println("Horaire:");
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < p; j++) {
				if (lignes[i][j].getValue() < 10)
					System.out.print(" ");
				if (lignes[i][j].getValue() < 100)
					System.out.print(" ");
				System.out.print(lignes[i][j].getValue());
				System.out.print("  ");
			}
			System.out.println("");
		}

		// Offre
		System.out.println("Offre:");
		for (int i = 0; i < p; i++) {
			if (offre[i].getValue() < 10)
				System.out.print(" ");
			if (offre[i].getValue() < 100)
				System.out.print(" ");
			System.out.print(offre[i].getValue());
			System.out.print("  ");
		}
		System.out.println("");

		// Demande
		System.out.println("Demande:");
		for (int i = 0; i < p; i++) {
			if (demande[i].getValue() < 10)
				System.out.print(" ");
			if (demande[i].getValue() < 100)
				System.out.print(" ");
			System.out.print(demande[i].getValue());
			System.out.print("  ");
		}
		System.out.println("");

		// Perte
		System.out.println("Perte:");
		for (int i = 0; i < p; i++) {
			if (perte[i].getValue() < 10)
				System.out.print(" ");
			if (perte[i].getValue() < 100)
				System.out.print(" ");
			System.out.print(perte[i].getValue());
			System.out.print("  ");
		}
		System.out.println("");

		Chatterbox.printStatistics(solver);
	}

	private static int nombreEmployesSouhaite(int indice) {
		int retour = 0;

		switch (indice) {
		case 0:
			retour = 1;
			break;
		case 1:
			retour = 2;
			break;
		case 2:
			retour = 3;
			break;
		case 3:
			retour = 4;
			break;
		case 4:
			retour = 5;
			break;
		case 5:
			retour = 4;
			break;
		case 6:
			retour = 2;
			break;
		case 7:
			retour = 3;
			break;
		case 8:
			retour = 4;
			break;
		case 9:
			retour = 3;
			break;
		case 10:
			retour = 5;
			break;
		case 11:
			retour = 5;
			break;
		case 12:
			retour = 4;
			break;
		case 13:
			retour = 3;
			break;
		case 14:
			retour = 3;
			break;
		case 15:
			retour = 3;
			break;
		}

		return retour;
	}
}
