/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2023 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jkiss.dbeaver.ui.gis;

/**
 * Geometry GeometryViewerConstants
 */
public class GeometryViewerConstants {

    public static final String PREF_MAX_OBJECTS_RENDER = "gis.view.maxObjectsRender";
    public static final String PREF_MIN_ZOOM_LEVEL = "gis.view.minZoomLevel";
    public static final String PREF_DEFAULT_SRID = "gis.view.defaultSRID";
    public static final String PREF_DEFAULT_LEAFLET_TILES = "gis.view.defaultLeafletTiles";
    public static final String PREF_SHOW_LABELS = "gis.view.showLabels";

    public static final int DEFAULT_MAX_OBJECTS_RENDER = 10000;

    // https://leafletjs.com/reference.html#tilelayer-minzoom
    public static final int DEFAULT_MIN_ZOOM_LEVEL = 0;
    public static final int DEFAULT_MAX_ZOOM_LEVEL = 18;
}
