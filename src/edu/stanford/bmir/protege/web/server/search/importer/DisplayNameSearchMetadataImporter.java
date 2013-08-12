package edu.stanford.bmir.protege.web.server.search.importer;

import edu.stanford.bmir.protege.web.server.search.EntityBasedSearchMDImporter;
import edu.stanford.bmir.protege.web.server.search.SearchMetadataDB;
import edu.stanford.bmir.protege.web.server.search.SearchMetadataImportContext;
import edu.stanford.bmir.protege.web.shared.search.SearchType;
import edu.stanford.bmir.protege.web.server.search.SearchMetadata;
import org.semanticweb.owlapi.model.OWLEntity;

import java.util.Set;

/**
 * Author: Matthew Horridge<br>
 * Stanford University<br>
 * Bio-Medical Informatics Research Group<br>
 * Date: 19/06/2013
 */
public class DisplayNameSearchMetadataImporter extends EntityBasedSearchMDImporter {

    public static final String GROUP_DESCRIPTION = "Display name";

    @Override
    public boolean isImporterFor(Set<SearchType> types) {
        return types.contains(SearchType.DISPLAY_NAME);
    }

    @Override
    public void generateSearchMetadataFor(OWLEntity entity, String entityRendering, SearchMetadataImportContext context, SearchMetadataDB searchMetadataDB) {
//        SearchMetadata searchMetadata = new SearchMetadata(SearchType.DISPLAY_NAME, GROUP_DESCRIPTION, entity, entityRendering, entityRendering);
//        searchMetadataDB.addResult(searchMetadata);
    }
}
