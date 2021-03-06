package edu.stanford.bmir.protege.web.shared.event;

import com.google.gwt.event.shared.EventHandler;

/**
 * Author: Matthew Horridge<br>
 * Stanford University<br>
 * Bio-Medical Informatics Research Group<br>
 * Date: 20/03/2013
 */
public interface EntityNotesChangedHandler extends EventHandler {

    void entityNotesChanged(EntityNotesChangedEvent event);

}
