package org.pdfsam.ui.dashboard.modules;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.css.PseudoClass;

public class DashboardTileArmed {
	private static final PseudoClass ARMED_PSEUDOCLASS_STATE = PseudoClass.getPseudoClass("armed");

	private DashboardTile parent;

	public DashboardTileArmed(DashboardTile parent) {
		this.parent = parent;
	}

	/**
	 * Property telling if the region (acting as a button) is armed
	 */
	ReadOnlyBooleanWrapper armed = new ReadOnlyBooleanWrapper(false) {
		@Override
		protected void invalidated() {
			parent.pseudoClassStateChanged(ARMED_PSEUDOCLASS_STATE, get());
		}
	};

	public final ReadOnlyBooleanProperty armedProperty() {
		return armed.getReadOnlyProperty();
	}

	public final boolean isArmed() {
		return armed.get();
	}

	public void bind(ReadOnlyBooleanProperty property) {
		armed.bind(property);
	}
}
