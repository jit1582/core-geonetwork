package org.fao.geonet.guiservices.metadata;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.fao.geonet.domain.*;
import org.fao.geonet.exceptions.OperationNotAllowedEx;
import jeeves.interfaces.Service;
import jeeves.server.ServiceConfig;
import jeeves.server.context.ServiceContext;
import org.fao.geonet.GeonetContext;
import org.fao.geonet.constants.Geonet;
import org.fao.geonet.kernel.DataManager;
import org.fao.geonet.repository.MetadataRepository;
import org.fao.geonet.repository.UserGroupRepository;
import org.fao.geonet.repository.specification.MetadataSpecs;
import org.fao.geonet.repository.specification.UserGroupSpecs;
import org.jdom.Element;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specifications;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

import static org.springframework.data.jpa.domain.Specifications.*;

/**
 * Retrieves the metadata owned by a user. Depending on user profile:
 *
 *   - Administrator: returns all metadata
 *   - Reviewer, UserAdmin: returns all metadata owned by the user's groups
 *   - Editor: return metadata owned by the user
 *   - Other profiles: throw exception OperationNotAllowedEx
 *
 * Service parameters:
 *
 *   - sortBySelect: allowed values (date, popularity, rating) (optional parameter)
 *
 */
public class GetByOwner implements Service {

    private static final String SORT_BY       = "sortBy";

    private Element _response;

    //--------------------------------------------------------------------------
    //---
    //--- Init
    //---
    //--------------------------------------------------------------------------

    public void init(String appPath, ServiceConfig config) throws Exception {
    }

    //--------------------------------------------------------------------------
    //---
    //--- Service
    //---
    //--------------------------------------------------------------------------

    public Element exec(Element params, ServiceContext context) throws Exception {
        GeonetContext gc = (GeonetContext) context.getHandlerContext(Geonet.CONTEXT_NAME);


        int ownerId = context.getUserSession().getUserIdAsInt();
        Profile userProfile = context.getUserSession().getProfile();

        if (userProfile == null) {
            throw new OperationNotAllowedEx("Unauthorized user attempted to list editable metadata ");
        }

        Specifications<Metadata> spec;
        // if the user is an admin, return all metadata
        if(userProfile == Profile.Administrator) {
            spec = where(MetadataSpecs.isHarvested(false));
        } else if(userProfile == Profile.Reviewer || userProfile == Profile.UserAdmin) {
            final List<UserGroup> groups = context.getBean(UserGroupRepository.class).findAll(UserGroupSpecs.hasUserId(ownerId));
            List<Integer> groupIds = Lists.transform(groups, new Function<UserGroup, Integer>() {
                @Nullable
                @Override
                public Integer apply(@Nonnull UserGroup input) {
                    return input.getId().getGroupId();
                }
            });
            spec = where(MetadataSpecs.isHarvested(false)).and(MetadataSpecs.isOwnedByOneOfFollowingGroups(groupIds));
            // if the user is a reviewer, return all metadata of the user's groups
        } else if(userProfile == Profile.Editor) {
            spec = where(MetadataSpecs.isOwnedByUser(ownerId)).and(MetadataSpecs.isHarvested(false));
            // if the user is an editor, return metadata owned by this user
        } else {
            throw new OperationNotAllowedEx("Unauthorized user " + ownerId + " attempted to list editable metadata ");
        }

        // Sorting
        String sortBy = sortByParameter(params);

        Sort order = null;
        if(sortBy.equals("date")) {
            order = new Sort(Sort.Direction.DESC, Metadata_.dataInfo + "." + MetadataDataInfo_.changeDate);
        } else if(sortBy.equals("popularity")) {
            order = new Sort(Sort.Direction.DESC, Metadata_.dataInfo + "." + MetadataDataInfo_.popularity);
        } else if(sortBy.equals("rating")) {
            order = new Sort(Sort.Direction.DESC, Metadata_.dataInfo + "." + MetadataDataInfo_.rating);
        } else {
            throw new IllegalArgumentException("Unknown sortBy parameter: "+sortBy);
        }

        List<Metadata> metadataList = context.getBean(MetadataRepository.class).findAll(spec, order);
        _response = new Element("response");

        for (Metadata rec : metadataList) {
            String  id = "" + rec.getId();
            boolean forEditing = false, withValidationErrors = false, keepXlinkAttributes = false;
            Element md = gc.getBean(DataManager.class).getMetadata(context, id, forEditing, withValidationErrors, keepXlinkAttributes);
            _response.addContent(md);
        }

        Element currentSortBySelect = new Element(SORT_BY);
        currentSortBySelect.setText(sortBy);
        _response.addContent(currentSortBySelect);
        Element response = (Element) _response.clone();
        return response;
    }

    private String sortByParameter(Element params) {
		Element sortByEl = params.getChild(SORT_BY);
		String sortBy = null;
		if (sortByEl == null) {
			sortBy = "date";
		}
        else {
			sortBy = sortByEl.getText();
		}
        return sortBy;
    }

}